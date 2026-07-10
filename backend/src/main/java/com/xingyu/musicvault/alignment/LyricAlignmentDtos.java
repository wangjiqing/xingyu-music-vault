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
            String preset,
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

    public record CreateManualLyricDraftRequest(
            String text,
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
            WorkerSignalsResponse workerSignals,
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
            Long importedLyricId,
            String draftStatus,
            Long confirmedTrustedLyricsId,
            ObservabilitySummaryResponse observabilitySummary
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
            WorkerSignalsResponse workerSignals,
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
            Long importedLyricId,
            String draftStatus,
            Long confirmedTrustedLyricsId,
            ObservabilitySummaryResponse observabilitySummary
    ) {
    }

    public record ObservabilitySummaryResponse(
            String workerStage,
            String workerStageLabel,
            String heartbeatAt,
            String heartbeatHealth,
            Long runningDurationSeconds,
            Long stageDurationSeconds,
            String preset,
            Integer warningCount,
            String errorCode,
            String errorSummary,
            String statusProtocolLabel
    ) {
    }

    public record LyricTaskObservabilityResponse(
            String jobId,
            String taskType,
            boolean statusAvailable,
            String statusParseError,
            boolean directoryAvailable,
            boolean legacy,
            java.util.List<String> compatibilityMessages,
            String workerState,
            String workerStateLabel,
            String workerStage,
            String workerStageLabel,
            String workerStageDescription,
            Integer statusSchemaVersion,
            Integer requestSchemaVersion,
            String startedAt,
            String stageStartedAt,
            String updatedAt,
            String heartbeatAt,
            String heartbeatHealth,
            Long runningDurationSeconds,
            Long stageDurationSeconds,
            JsonNode progress,
            WorkerAttemptResponse attempt,
            JsonNode requestedConfig,
            JsonNode resolvedConfig,
            JsonNode configSummary,
            JsonNode warnings,
            WorkerErrorResponse error,
            JsonNode result,
            java.util.List<WorkerOutputResponse> outputs,
            WorkerMarkersResponse markers,
            java.util.List<WorkerEventResponse> events,
            boolean eventsAvailable,
            boolean eventsTruncated,
            String eventsReadError,
            boolean rawStatusAvailable
    ) {
    }

    public record WorkerAttemptResponse(
            String id,
            Integer number,
            String stderrPath
    ) {
    }

    public record WorkerErrorResponse(
            String code,
            String label,
            String message,
            JsonNode details
    ) {
    }

    public record WorkerOutputResponse(
            String type,
            boolean available,
            String relativePath
    ) {
    }

    public record WorkerMarkersResponse(
            boolean ready,
            boolean running,
            boolean succeeded,
            boolean needsReview,
            boolean failed,
            boolean abandoned
    ) {
    }

    public record WorkerEventResponse(
            String eventId,
            String timestamp,
            String level,
            String type,
            String stage,
            String message,
            JsonNode details
    ) {
    }

    public record LyricDraftDefaultOptionsResponse(
            String language,
            String asrModel,
            boolean skipSeparation,
            boolean vadFilter,
            boolean conditionOnPreviousText,
            boolean keepSuspectedMetadata,
            boolean retainIntermediate
    ) {
    }

    public record LyricDraftTrustedAssetResponse(
            Long id,
            String sourceType,
            String contentHash,
            LocalDateTime confirmedAt,
            String confirmedBy
    ) {
    }

    public record WorkerSignalsResponse(
            boolean jobDirectoryAvailable,
            boolean ready,
            boolean running,
            boolean succeeded,
            boolean needsReview,
            boolean failed,
            boolean abandoned,
            boolean statusJsonAvailable,
            boolean resultDirectoryAvailable,
            boolean stderrLogAvailable,
            String stageMessage
    ) {
    }

    public record MusicLyricDraftContextResponse(
            Long musicId,
            LyricDraftDefaultOptionsResponse defaultOptions,
            AlignmentJobResponse latestJob,
            LyricDraftResponse draft,
            LyricDraftTrustedAssetResponse trustedLyricsAsset
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
            String errorMessage,
            String sourceType,
            JsonNode sourceMetadata,
            java.util.List<LyricDraftSourceResponse> sources
    ) {
    }

    public record LyricDraftSourceRequest(
            String provider,
            String query,
            String title,
            String url,
            String domain,
            String selectedBy
    ) {
    }

    public record LyricDraftSourceResponse(
            Long id,
            String provider,
            String query,
            String title,
            String url,
            String domain,
            String selectedBy,
            LocalDateTime selectedAt
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
