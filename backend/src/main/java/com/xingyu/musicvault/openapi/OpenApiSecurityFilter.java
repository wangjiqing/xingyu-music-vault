package com.xingyu.musicvault.openapi;

import io.vertx.core.http.HttpServerRequest;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import java.util.Comparator;
import java.net.URLEncoder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class OpenApiSecurityFilter implements ContainerRequestFilter, ContainerResponseFilter {
    static final String TRACE_ID_PROPERTY = "xingyu.openapi.traceId";
    private static final String START_NANOS_PROPERTY = "xingyu.openapi.startNanos";
    private static final String CLIENT_IP_PROPERTY = "xingyu.openapi.clientIp";
    private static final String OPENAPI_PREFIX = "api/open/v1/";
    private static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final int RATE_LIMIT_WINDOW_SECONDS = 60;
    private static final Logger LOG = Logger.getLogger(OpenApiSecurityFilter.class);

    @Inject
    OpenApiSecurityConfig config;

    @Inject
    OpenApiRateLimiter rateLimiter;

    @Inject
    OpenApiAccessLogFormatter accessLogFormatter;

    @Inject
    OpenApiCredentialService credentialService;

    @Inject
    OpenApiCredentialCryptoService cryptoService;

    @Context
    HttpServerRequest request;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!isOpenApiPath(requestContext)) {
            return;
        }

        String traceId = UUID.randomUUID().toString();
        String clientIp = resolveClientIp(requestContext);
        requestContext.setProperty(TRACE_ID_PROPERTY, traceId);
        requestContext.setProperty(START_NANOS_PROPERTY, System.nanoTime());
        requestContext.setProperty(CLIENT_IP_PROPERTY, clientIp);

        OpenApiAuthResult authResult = authenticate(requestContext, clientIp);
        if (!authResult.ok()) {
            requestContext.abortWith(error(
                    authResult.status(),
                    authResult.code(),
                    authResult.message(),
                    traceId,
                    authResult.details()
            ));
            return;
        }

        if (config.rateLimit().enabled()) {
            int limit = config.rateLimit().requestsPerMinute();
            if (!rateLimiter.allow(clientIp, limit)) {
                requestContext.abortWith(error(
                        Response.Status.TOO_MANY_REQUESTS,
                        "OPENAPI_RATE_LIMITED",
                        "Too many OpenAPI requests",
                        traceId,
                        Map.of("limit", limit, "windowSeconds", RATE_LIMIT_WINDOW_SECONDS)
                ));
            }
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        if (!config.accessLog().enabled() || !isOpenApiPath(requestContext)) {
            return;
        }
        long durationMs = durationMs(requestContext.getProperty(START_NANOS_PROPERTY));
        String traceId = String.valueOf(requestContext.getProperty(TRACE_ID_PROPERTY));
        String clientIp = String.valueOf(requestContext.getProperty(CLIENT_IP_PROPERTY));
        LOG.info(accessLogFormatter.format(
                requestContext.getMethod(),
                requestContext.getUriInfo().getPath(),
                responseContext.getStatus(),
                durationMs,
                clientIp,
                traceId
        ));
    }

    private OpenApiAuthResult authenticate(ContainerRequestContext requestContext, String clientIp) {
        try {
            cryptoService.requireMasterKey();
        } catch (IllegalStateException ex) {
            LOG.error("OpenAPI credential master key is not configured");
            return OpenApiAuthResult.error(Response.Status.INTERNAL_SERVER_ERROR, "OPENAPI_CONFIG_ERROR", "OpenAPI credential master key is not configured");
        }

        String accessKey = requestContext.getHeaderString("X-Xingyu-Access-Key");
        String timestamp = requestContext.getHeaderString("X-Xingyu-Timestamp");
        String nonce = requestContext.getHeaderString("X-Xingyu-Nonce");
        String signatureVersion = requestContext.getHeaderString("X-Xingyu-Signature-Version");
        String signature = requestContext.getHeaderString("X-Xingyu-Signature");

        if (blank(accessKey) || blank(timestamp) || blank(nonce) || blank(signatureVersion) || blank(signature)) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_UNAUTHORIZED", "Missing OpenAPI HMAC authentication headers");
        }
        if (!"v1".equals(signatureVersion)) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_UNAUTHORIZED", "Unsupported OpenAPI signature version");
        }
        if (!timestampWithinWindow(timestamp)) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_UNAUTHORIZED", "Invalid or expired OpenAPI timestamp");
        }
        OpenApiCredential credential = credentialService.findByAccessKey(accessKey).orElse(null);
        if (credential == null) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_UNAUTHORIZED", "Invalid OpenAPI credential");
        }
        if (!credential.enabled) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_CREDENTIAL_DISABLED", "OpenAPI credential is disabled");
        }
        if (credential.expiresAt != null && credential.expiresAt.isBefore(now())) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_CREDENTIAL_EXPIRED", "OpenAPI credential is expired");
        }
        if (!credentialService.hasScope(credential, requiredScope(requestContext))) {
            return OpenApiAuthResult.error(Response.Status.FORBIDDEN, "OPENAPI_FORBIDDEN", "OpenAPI credential scope is insufficient");
        }

        byte[] body;
        try {
            body = readBody(requestContext);
        } catch (OpenApiException ex) {
            return OpenApiAuthResult.error(ex.status(), ex.code(), ex.getMessage(), ex.details());
        }
        String canonicalString = canonicalString(requestContext, body, timestamp, nonce);
        String expected = cryptoService.hmacSha256Hex(credentialService.decryptSecret(credential), canonicalString);
        if (!cryptoService.secureEquals(expected, signature.toLowerCase(java.util.Locale.ROOT))) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_UNAUTHORIZED", "Invalid OpenAPI signature");
        }
        if (!credentialService.recordNonce(accessKey, nonce, timestamp)) {
            return OpenApiAuthResult.error(Response.Status.UNAUTHORIZED, "OPENAPI_UNAUTHORIZED", "Repeated OpenAPI nonce");
        }
        credentialService.recordLastUsed(credential, clientIp, requestContext.getHeaderString("User-Agent"));
        return OpenApiAuthResult.allowed();
    }

    String resolveClientIp(ContainerRequestContext requestContext) {
        String forwardedFor = requestContext.getHeaderString("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            String first = forwardedFor.split(",", 2)[0].trim();
            if (!first.isBlank()) {
                return first;
            }
        }
        String realIp = requestContext.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        if (request != null && request.remoteAddress() != null && request.remoteAddress().host() != null) {
            return request.remoteAddress().host();
        }
        return "unknown";
    }

    private Response error(Response.Status status, String code, String message, String traceId, Map<String, Object> details) {
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new OpenApiExceptionMapper.OpenApiErrorResponse(code, message, traceId, details))
                .build();
    }

    private OpenApiScope requiredScope(ContainerRequestContext requestContext) {
        return "GET".equalsIgnoreCase(requestContext.getMethod()) ? OpenApiScope.OPENAPI_READ : OpenApiScope.OPENAPI_WRITE;
    }

    private boolean timestampWithinWindow(String timestamp) {
        try {
            long epochMillis = Long.parseLong(timestamp);
            long deltaMillis = Math.abs(Instant.now().toEpochMilli() - epochMillis);
            return deltaMillis <= config.hmac().timestampWindowSeconds() * 1_000L;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private byte[] readBody(ContainerRequestContext requestContext) {
        try {
            long maxBodyBytes = config.hmac().maxBodyBytes();
            if (maxBodyBytes < 0) {
                maxBodyBytes = 0;
            }
            int contentLength = requestContext.getLength();
            if (contentLength > maxBodyBytes) {
                throw payloadTooLarge(maxBodyBytes);
            }
            if (requestContext.getEntityStream() == null) {
                return new byte[0];
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream(Math.max(0, contentLength));
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = requestContext.getEntityStream().read(buffer)) != -1) {
                total += read;
                if (total > maxBodyBytes) {
                    throw payloadTooLarge(maxBodyBytes);
                }
                output.write(buffer, 0, read);
            }
            byte[] bytes = output.toByteArray();
            requestContext.setEntityStream(new ByteArrayInputStream(bytes));
            return bytes;
        } catch (OpenApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new OpenApiException(Response.Status.UNAUTHORIZED, "OPENAPI_UNAUTHORIZED", "Unable to read OpenAPI request body");
        }
    }

    private OpenApiException payloadTooLarge(long maxBodyBytes) {
        return new OpenApiException(
                Response.Status.REQUEST_ENTITY_TOO_LARGE,
                "OPENAPI_PAYLOAD_TOO_LARGE",
                "OpenAPI request body is too large",
                Map.of("maxBodyBytes", maxBodyBytes)
        );
    }

    private String canonicalString(ContainerRequestContext requestContext, byte[] body, String timestamp, String nonce) {
        return String.join("\n",
                requestContext.getMethod().toUpperCase(java.util.Locale.ROOT),
                canonicalPathWithQuery(requestContext),
                body.length == 0 ? EMPTY_SHA256 : cryptoService.sha256Hex(body),
                timestamp,
                nonce
        );
    }

    private String canonicalPathWithQuery(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        List<Map.Entry<String, List<String>>> entries = requestContext.getUriInfo().getQueryParameters().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        String query = entries.stream()
                .flatMap(entry -> entry.getValue().stream()
                        .sorted(Comparator.naturalOrder())
                        .map(value -> encode(entry.getKey()) + "=" + encode(value)))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return query.isBlank() ? path : path + "?" + query;
    }

    private String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.systemDefault());
    }

    private boolean isOpenApiPath(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo() == null ? "" : requestContext.getUriInfo().getPath();
        if (path == null) {
            return false;
        }
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        return normalized.equals("api/open/v1") || normalized.startsWith(OPENAPI_PREFIX);
    }

    private long durationMs(Object startNanos) {
        if (!(startNanos instanceof Long start)) {
            return 0;
        }
        return Math.max(0, (System.nanoTime() - start) / 1_000_000L);
    }

    private record OpenApiAuthResult(boolean ok, Response.Status status, String code, String message, Map<String, Object> details) {
        static OpenApiAuthResult allowed() {
            return new OpenApiAuthResult(true, Response.Status.OK, "", "", Map.of());
        }

        static OpenApiAuthResult error(Response.Status status, String code, String message) {
            return error(status, code, message, Map.of());
        }

        static OpenApiAuthResult error(Response.Status status, String code, String message, Map<String, Object> details) {
            return new OpenApiAuthResult(false, status, code, message, details == null ? Map.of() : details);
        }
    }
}
