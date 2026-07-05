package com.xingyu.musicvault.search;

import java.time.LocalDateTime;
import java.util.List;

public final class BraveSearchDtos {
    private BraveSearchDtos() {
    }

    public record BraveSearchStatusResponse(
            boolean configured,
            boolean enabled,
            boolean searchable,
            String mode,
            String message,
            boolean encryptionAvailable,
            LocalDateTime updatedAt,
            LocalDateTime lastCheckedAt,
            String lastError
    ) {
    }

    public record SaveBraveKeyRequest(
            String apiKey,
            String updatedBy
    ) {
    }

    public record SetBraveEnabledRequest(
            boolean enabled,
            String updatedBy
    ) {
    }

    public record BraveSearchRequest(
            String query,
            Integer count
    ) {
    }

    public record BraveSearchResponse(
            String query,
            List<BraveSearchResultResponse> results
    ) {
    }

    public record BraveSearchResultResponse(
            String title,
            String url,
            String domain,
            String description
    ) {
    }
}
