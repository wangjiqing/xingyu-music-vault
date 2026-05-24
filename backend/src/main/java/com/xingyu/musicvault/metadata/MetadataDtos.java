package com.xingyu.musicvault.metadata;

import java.time.LocalDateTime;
import java.util.List;

public final class MetadataDtos {
    private MetadataDtos() {
    }

    public record MetadataSnapshot(
            String title,
            String artist,
            String album,
            String albumArtist,
            Integer year,
            String genre,
            Integer trackNumber,
            Long duration
    ) {
    }

    public record MetadataDiffItem(
            String field,
            Object databaseValue,
            Object embeddedValue
    ) {
    }

    public record MetadataCompareResponse(
            Long musicId,
            MetadataSnapshot database,
            MetadataSnapshot embedded,
            List<MetadataDiffItem> diffs
    ) {
    }

    public record MetadataSyncRequest(
            String mode
    ) {
    }

    public record MetadataSyncResult(
            Long musicId,
            String direction,
            String mode,
            String status,
            MetadataSnapshot beforeDatabase,
            MetadataSnapshot afterDatabase,
            MetadataSnapshot beforeFile,
            MetadataSnapshot afterFile,
            List<String> changedFields,
            Long auditId,
            String errorMessage
    ) {
    }

    public record BatchMetadataCompareRequest(
            List<Long> musicIds
    ) {
    }

    public record BatchMetadataSyncRequest(
            List<Long> musicIds,
            String mode
    ) {
    }

    public record BatchMetadataSyncResponse(
            String batchId,
            int total,
            int success,
            int failed,
            List<MetadataSyncResult> items
    ) {
    }

    public record MetadataAuditCreateRequest(
            String batchId,
            Long musicId,
            String filePath,
            String direction,
            String sourceType,
            String targetType,
            String mode,
            String operationType,
            MetadataSnapshot beforeDatabase,
            MetadataSnapshot afterDatabase,
            MetadataSnapshot beforeFile,
            MetadataSnapshot afterFile,
            List<String> changedFields,
            String status,
            String errorMessage,
            LocalDateTime createdAt,
            String createdBy
    ) {
    }
}
