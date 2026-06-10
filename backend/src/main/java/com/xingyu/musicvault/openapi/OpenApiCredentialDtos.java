package com.xingyu.musicvault.openapi;

import java.time.LocalDateTime;
import java.util.List;

public final class OpenApiCredentialDtos {
    private OpenApiCredentialDtos() {
    }

    public record CreateCredentialRequest(
            String name,
            String description,
            List<String> scopes,
            LocalDateTime expiresAt
    ) {
    }

    public record SetCredentialEnabledRequest(boolean enabled) {
    }

    public record CredentialResponse(
            Long id,
            String name,
            String accessKey,
            String secretFingerprint,
            List<String> scopes,
            boolean enabled,
            String description,
            LocalDateTime expiresAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime lastUsedAt,
            String lastUsedIp,
            String lastUsedUserAgent
    ) {
        static CredentialResponse from(OpenApiCredential credential, List<String> scopes) {
            return new CredentialResponse(
                    credential.id,
                    credential.name,
                    credential.accessKey,
                    credential.secretFingerprint,
                    scopes,
                    credential.enabled,
                    credential.description,
                    credential.expiresAt,
                    credential.createdAt,
                    credential.updatedAt,
                    credential.lastUsedAt,
                    credential.lastUsedIp,
                    credential.lastUsedUserAgent
            );
        }
    }

    public record CreatedCredentialResponse(
            Long id,
            String name,
            String accessKey,
            String secretKey,
            String secretFingerprint,
            List<String> scopes,
            boolean enabled,
            LocalDateTime createdAt,
            LocalDateTime expiresAt
    ) {
    }
}
