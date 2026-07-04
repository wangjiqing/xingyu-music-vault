package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;

public final class LyricAlignmentDtos {
    private LyricAlignmentDtos() {
    }

    public record CreateAlignmentJobRequest(
            Long songId,
            Long sourceLyricsAssetId,
            String createdBy,
            JsonNode sections,
            JsonNode workerOptions
    ) {
    }

    public record CreateLyricDraftJobRequest(
            String language,
            String asrModel,
            Boolean skipSeparation,
            Boolean vadFilter,
            Boolean conditionOnPreviousText,
            Boolean keepSuspectedMetadata,
            Boolean retainIntermediate,
            String createdBy
    ) {
    }

    public record AlignmentJobResponse(
            String id,
            String taskType,
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
            String taskType,
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

    public record LyricDraftResponse(
            String jobId,
            Long musicId,
            String executionStatus,
            String draftStatus,
            String originalText,
            String originalTextHash,
            String editableText,
            String editableTextHash,
            JsonNode reportSummary,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            String editedBy,
            LocalDateTime editedAt,
            String confirmedBy,
            LocalDateTime confirmedAt,
            Long confirmedTrustedLyricsId,
            String rejectedBy,
            LocalDateTime rejectedAt,
            String rejectNote,
            String errorMessage
    ) {
    }

    public record UpdateLyricDraftRequest(
            String text,
            String editedBy
    ) {
    }

    public record ConfirmLyricDraftRequest(
            String note,
            String confirmedBy
    ) {
    }

    public record ConfirmLyricDraftResponse(
            String jobId,
            Long draftId,
            Long trustedLyricsId,
            String draftStatus,
            String editableTextHash,
            LocalDateTime confirmedAt,
            String confirmedBy
    ) {
    }

    public record RejectLyricDraftRequest(
            String rejectNote,
            String rejectedBy
    ) {
    }
}
