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

    public record MetadataCompareSnapshot(
            String title,
            String artist,
            String album
    ) {
    }

    public record MetadataCompareResponse(
            Long musicId,
            MetadataCompareSnapshot database,
            MetadataCompareSnapshot embedded,
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

    public record MetadataAuditListItem(
            Long id,
            String batchId,
            Long musicId,
            String musicTitle,
            String filePath,
            String direction,
            String sourceType,
            String targetType,
            String operationType,
            String status,
            String rollbackStatus,
            List<String> changedFields,
            LocalDateTime createdAt,
            String errorMessage
    ) {
    }

    public record MetadataAuditPageResponse(
            List<MetadataAuditListItem> items,
            long total,
            int page,
            int pageSize
    ) {
    }

    public record MetadataAuditDetailResponse(
            Long id,
            String batchId,
            Long musicId,
            String musicTitle,
            String filePath,
            String direction,
            String sourceType,
            String targetType,
            String mode,
            String operationType,
            String status,
            String rollbackStatus,
            MetadataSnapshot beforeDatabase,
            MetadataSnapshot afterDatabase,
            MetadataSnapshot beforeFile,
            MetadataSnapshot afterFile,
            List<String> changedFields,
            String errorMessage,
            Long rollbackOfAuditId,
            Long rollbackAuditId,
            LocalDateTime createdAt,
            String createdBy
    ) {
    }

    public record MetadataRollbackPreviewResponse(
            Long auditId,
            Long musicId,
            String rollbackTarget,
            MetadataSnapshot current,
            MetadataSnapshot target,
            List<MetadataDiffItem> diffs,
            boolean canRollback,
            List<String> warnings,
            String errorMessage
    ) {
    }

    public record MetadataRollbackRequest(
            Boolean confirm
    ) {
    }

    public record MetadataRollbackResult(
            Long auditId,
            Long rollbackAuditId,
            boolean success,
            String message,
            String errorMessage
    ) {
    }

    public record BatchMetadataRollbackRequest(
            List<Long> auditIds,
            Boolean confirm
    ) {
    }

    public record BatchMetadataRollbackPreviewResponse(
            int total,
            int canRollbackCount,
            int cannotRollbackCount,
            List<MetadataRollbackPreviewResponse> items
    ) {
    }

    public record BatchMetadataRollbackResponse(
            String batchId,
            int total,
            int success,
            int failed,
            List<MetadataRollbackResult> items
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
            Long rollbackOfAuditId,
            LocalDateTime createdAt,
            String createdBy
    ) {
    }
}
