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
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;

@Provider
@ApplicationScoped
@Priority(Priorities.AUTHENTICATION)
public class OpenApiSecurityFilter implements ContainerRequestFilter, ContainerResponseFilter {
    static final String TRACE_ID_PROPERTY = "xingyu.openapi.traceId";
    private static final String START_NANOS_PROPERTY = "xingyu.openapi.startNanos";
    private static final String CLIENT_IP_PROPERTY = "xingyu.openapi.clientIp";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String OPENAPI_PREFIX = "api/open/v1/";
    private static final int WINDOW_SECONDS = 60;
    private static final Logger LOG = Logger.getLogger(OpenApiSecurityFilter.class);

    @Inject
    OpenApiSecurityConfig config;

    @Inject
    OpenApiRateLimiter rateLimiter;

    @Inject
    OpenApiAccessLogFormatter accessLogFormatter;

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

        if (config.auth().enabled() && configuredToken().isBlank()) {
            LOG.error("OpenAPI auth is enabled but xingyu.openapi.auth.token is blank");
            requestContext.abortWith(error(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "OPENAPI_CONFIG_ERROR",
                    "OpenAPI token is not configured",
                    traceId,
                    Map.of()
            ));
            return;
        }

        if (config.auth().enabled() && !authorized(requestContext)) {
            requestContext.abortWith(error(
                    Response.Status.UNAUTHORIZED,
                    "OPENAPI_UNAUTHORIZED",
                    "Missing or invalid OpenAPI token",
                    traceId,
                    Map.of()
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
                        Map.of("limit", limit, "windowSeconds", WINDOW_SECONDS)
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

    private boolean authorized(ContainerRequestContext requestContext) {
        String expected = configuredToken();
        String provided = bearerToken(requestContext.getHeaderString("Authorization"));
        if (provided == null || provided.isBlank()) {
            provided = requestContext.getHeaderString("X-Xingyu-Api-Token");
        }
        return secureEquals(expected, provided);
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorization.substring(BEARER_PREFIX.length());
    }

    private String configuredToken() {
        return config.auth().token().orElse("");
    }

    private boolean secureEquals(String expected, String provided) {
        if (expected == null || provided == null) {
            return false;
        }
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] providedBytes = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, providedBytes);
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
}
