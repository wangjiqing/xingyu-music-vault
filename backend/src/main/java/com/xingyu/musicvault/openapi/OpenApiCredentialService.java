package com.xingyu.musicvault.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.openapi.OpenApiCredentialDtos.CreateCredentialRequest;
import com.xingyu.musicvault.openapi.OpenApiCredentialDtos.CreatedCredentialResponse;
import com.xingyu.musicvault.openapi.OpenApiCredentialDtos.CredentialResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class OpenApiCredentialService {
    private static final Logger LOG = Logger.getLogger(OpenApiCredentialService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @Inject
    OpenApiCredentialCryptoService cryptoService;

    @Inject
    ObjectMapper objectMapper;

    @ConfigProperty(name = "xingyu.openapi.hmac.nonce-ttl-seconds", defaultValue = "600")
    long nonceTtlSeconds;

    @Transactional
    public CreatedCredentialResponse create(CreateCredentialRequest request) {
        requireConfiguredMasterKey();
        String name = normalizeName(request == null ? null : request.name());
        List<String> scopes = normalizeScopes(request == null ? null : request.scopes());
        String secret = cryptoService.randomCredential("xmv_sk_");

        OpenApiCredential credential = new OpenApiCredential();
        credential.name = name;
        credential.description = normalizeOptional(request == null ? null : request.description());
        credential.scopesJson = writeScopes(scopes);
        credential.accessKey = uniqueAccessKey();
        credential.secretEncrypted = cryptoService.encryptSecret(secret);
        credential.secretFingerprint = cryptoService.fingerprint(secret);
        credential.enabled = true;
        credential.expiresAt = request == null ? null : request.expiresAt();
        credential.persist();

        return new CreatedCredentialResponse(
                credential.id,
                credential.name,
                credential.accessKey,
                secret,
                credential.secretFingerprint,
                scopes,
                credential.enabled,
                credential.createdAt,
                credential.expiresAt
        );
    }

    @Transactional
    public List<CredentialResponse> list() {
        return OpenApiCredential.<OpenApiCredential>listAll().stream()
                .map(credential -> CredentialResponse.from(credential, readScopes(credential)))
                .toList();
    }

    @Transactional
    public CredentialResponse setEnabled(Long id, boolean enabled) {
        OpenApiCredential credential = findById(id);
        credential.enabled = enabled;
        return CredentialResponse.from(credential, readScopes(credential));
    }

    @Transactional
    public void delete(Long id) {
        OpenApiCredential credential = findById(id);
        credential.delete();
    }

    public Optional<OpenApiCredential> findByAccessKey(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            return Optional.empty();
        }
        return OpenApiCredential.find("accessKey", accessKey).firstResultOptional();
    }

    public boolean hasScope(OpenApiCredential credential, OpenApiScope requiredScope) {
        return readScopes(credential).contains(requiredScope.name());
    }

    public String decryptSecret(OpenApiCredential credential) {
        return cryptoService.decryptSecret(credential.secretEncrypted);
    }

    @Transactional
    public boolean recordNonce(String accessKey, String nonce, String timestamp) {
        cleanupExpiredNonces();
        if (OpenApiRequestNonce.count("accessKey = ?1 and nonce = ?2", accessKey, nonce) > 0) {
            return false;
        }
        OpenApiRequestNonce entity = new OpenApiRequestNonce();
        entity.accessKey = accessKey;
        entity.nonce = nonce;
        entity.requestTimestamp = fromEpochMillis(Long.parseLong(timestamp));
        entity.expiresAt = LocalDateTime.now().plusSeconds(nonceTtlSeconds);
        try {
            entity.persistAndFlush();
            return true;
        } catch (PersistenceException ex) {
            if (isConstraintViolation(ex)) {
                return false;
            }
            throw ex;
        }
    }

    @Transactional
    public void recordLastUsed(OpenApiCredential credential, String ip, String userAgent) {
        try {
            OpenApiCredential managed = OpenApiCredential.findById(credential.id);
            if (managed == null) {
                return;
            }
            managed.lastUsedAt = LocalDateTime.now();
            managed.lastUsedIp = truncate(ip, 128);
            managed.lastUsedUserAgent = truncate(userAgent, 512);
        } catch (RuntimeException ex) {
            LOG.warn("Failed to update OpenAPI credential last-used metadata");
        }
    }

    List<String> readScopes(OpenApiCredential credential) {
        try {
            return objectMapper.readValue(credential.scopesJson, STRING_LIST);
        } catch (Exception ex) {
            LOG.warnf(ex, "Failed to parse OpenAPI credential scopes for credential id=%s accessKey=%s",
                    credential == null ? null : credential.id,
                    credential == null ? null : credential.accessKey);
            return List.of();
        }
    }

    private boolean isConstraintViolation(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private OpenApiCredential findById(Long id) {
        OpenApiCredential credential = OpenApiCredential.findById(id);
        if (credential == null) {
            throw new NotFoundException("OpenAPI credential not found");
        }
        return credential;
    }

    private void requireConfiguredMasterKey() {
        try {
            cryptoService.requireMasterKey();
        } catch (IllegalStateException ex) {
            throw new OpenApiException(
                    Response.Status.INTERNAL_SERVER_ERROR,
                    "OPENAPI_CONFIG_ERROR",
                    "OpenAPI credential master key is not configured"
            );
        }
    }

    private String uniqueAccessKey() {
        for (int i = 0; i < 5; i++) {
            String accessKey = cryptoService.randomCredential("xmv_ak_");
            if (OpenApiCredential.count("accessKey", accessKey) == 0) {
                return accessKey;
            }
        }
        throw new IllegalStateException("Failed to generate unique OpenAPI access key");
    }

    private String normalizeName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new BadRequestException("name must not be blank");
        }
        return name.trim();
    }

    private String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private List<String> normalizeScopes(List<String> rawScopes) {
        if (rawScopes == null || rawScopes.isEmpty()) {
            throw new BadRequestException("scopes must not be empty");
        }
        EnumSet<OpenApiScope> allowed = EnumSet.allOf(OpenApiScope.class);
        List<String> scopes = new ArrayList<>();
        for (String rawScope : rawScopes) {
            String scope = rawScope == null ? "" : rawScope.trim().toUpperCase(Locale.ROOT);
            OpenApiScope parsed;
            try {
                parsed = OpenApiScope.valueOf(scope);
            } catch (RuntimeException ex) {
                throw new BadRequestException("unsupported scope: " + rawScope);
            }
            if (!allowed.contains(parsed) || scopes.contains(parsed.name())) {
                continue;
            }
            scopes.add(parsed.name());
        }
        if (scopes.isEmpty()) {
            throw new BadRequestException("scopes must not be empty");
        }
        return scopes;
    }

    private String writeScopes(List<String> scopes) {
        try {
            return objectMapper.writeValueAsString(scopes);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize OpenAPI scopes", ex);
        }
    }

    private void cleanupExpiredNonces() {
        OpenApiRequestNonce.delete("expiresAt < ?1", LocalDateTime.now());
    }

    private LocalDateTime fromEpochMillis(long epochMillis) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault());
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
