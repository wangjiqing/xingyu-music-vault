package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public final class LyricAlignmentDtos {
    private LyricAlignmentDtos() {
    }

    public record CreateAlignmentJobRequest(
            Long songId,
            String createdBy,
            JsonNode sections,
            JsonNode workerOptions
    ) {
    }

    public record AlignmentJobResponse(
            String id,
            Long songId,
            Long lyricId,
            String status,
            String reviewStatus,
            String importStatus,
            String workerOutcome,
            String audioRelativePath,
            String workerAudioPath,
            String trustedLyricsHash,
            String trustedLyricsSnapshot,
            JsonNode requestSnapshot,
            String errorMessage,
            JsonNode resultSummary,
            JsonNode workerStatus,
            String alignmentJsonHash,
            String lrcHash,
            String swlrcHash,
            String reportHash,
            boolean resultAvailable,
            String syncMessage,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime queuedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime failedAt,
            String reviewedBy,
            LocalDateTime reviewedAt,
            String reviewNote,
            String importedBy,
            LocalDateTime importedAt,
            String importErrorMessage,
            Long importedLyricId
    ) {
    }

    public record AlignmentJobListItemResponse(
            String id,
            Long songId,
            Long lyricId,
            String status,
            String reviewStatus,
            String importStatus,
            String workerOutcome,
            String audioRelativePath,
            String trustedLyricsHash,
            String errorMessage,
            JsonNode resultSummary,
            String alignmentJsonHash,
            String lrcHash,
            String swlrcHash,
            String reportHash,
            boolean resultAvailable,
            String syncMessage,
            String createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime queuedAt,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            LocalDateTime failedAt,
            String reviewedBy,
            LocalDateTime reviewedAt,
            String importedBy,
            LocalDateTime importedAt,
            String importErrorMessage,
            Long importedLyricId
    ) {
    }

    public record ReviewAlignmentJobRequest(
            String reviewNote,
            String reviewedBy
    ) {
    }

    public record ImportAlignmentJobRequest(
            String importedBy
    ) {
    }

    public record ImportAlignmentJobResponse(
            String jobId,
            Long songId,
            Long lyricId,
            Long importedLyricId,
            String importStatus,
            String lrcHash,
            String swlrcHash,
            LocalDateTime importedAt,
            String importedBy
    ) {
    }

    public record ArtifactContent(
            String fileName,
            String mediaType,
            byte[] content
    ) {
    }
}
