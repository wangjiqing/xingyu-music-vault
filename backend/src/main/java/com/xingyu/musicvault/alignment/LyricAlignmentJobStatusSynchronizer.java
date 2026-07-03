package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@ApplicationScoped
public class LyricAlignmentJobStatusSynchronizer {
    private static final Logger LOG = Logger.getLogger(LyricAlignmentJobStatusSynchronizer.class);

    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_ABANDONED = "ABANDONED";
    private static final String REVIEW_NOT_AVAILABLE = "NOT_AVAILABLE";
    private static final String REVIEW_PENDING = "PENDING";
    private static final List<String> TERMINAL_STATUSES = List.of(STATUS_COMPLETED, STATUS_FAILED, STATUS_ABANDONED);

    @Inject
    LyricAlignmentJobRepository jobRepository;

    @Inject
    LyricAlignmentWorkerStatusReader workerStatusReader;

    @Inject
    LyricAlignmentResultReader resultReader;

    @Inject
    ObjectMapper objectMapper;

    @Scheduled(every = "{music-vault.alignment-status-sync-interval}")
    void scheduledSync() {
        synchronizeActiveJobs();
    }

    public void synchronizeActiveJobs() {
        List<String> jobIds = jobRepository.findSynchronizableJobs().stream()
                .map(job -> job.id)
                .toList();
        for (String jobId : jobIds) {
            try {
                synchronize(jobId);
            } catch (RuntimeException exception) {
                LOG.errorf(exception, "Failed to synchronize alignment job status: jobId=%s", jobId);
            }
        }
    }

    public void synchronize(String jobId) {
        LyricAlignmentJob job = jobRepository.findById(jobId);
        if (job == null || TERMINAL_STATUSES.contains(job.status)) {
            return;
        }

        Path jobDir = Path.of(job.jobDir).toAbsolutePath().normalize();
        LyricAlignmentWorkerStatusReader.WorkerStatusSnapshot statusSnapshot = workerStatusReader.read(jobDir);
        QuarkusTransaction.requiringNew().run(() -> applySnapshot(jobId, jobDir, statusSnapshot));
    }

    private void applySnapshot(
            String jobId,
            Path jobDir,
            LyricAlignmentWorkerStatusReader.WorkerStatusSnapshot statusSnapshot
    ) {
        LyricAlignmentJob job = jobRepository.findById(jobId);
        if (job == null || TERMINAL_STATUSES.contains(job.status)) {
            return;
        }

        job.workerStatusJson = statusSnapshot.statusJsonRaw() == null ? job.workerStatusJson : statusSnapshot.statusJsonRaw();
        job.syncMessage = statusSnapshot.syncMessage();

        String workerStatus = normalizeWorkerStatus(statusSnapshot.status());
        if ("FAILED".equals(workerStatus) || statusSnapshot.failed()) {
            transitionToFailed(job, "FAILED", jobDir);
            return;
        }
        if ("ABANDONED".equals(workerStatus) || statusSnapshot.abandoned()) {
            transitionToAbandoned(job, jobDir);
            return;
        }
        if ("NEEDS_REVIEW".equals(workerStatus) || statusSnapshot.needsReview()) {
            transitionToCompleted(job, "NEEDS_REVIEW", jobDir);
            return;
        }
        if ("SUCCEEDED".equals(workerStatus) || statusSnapshot.succeeded()) {
            transitionToCompleted(job, "SUCCEEDED", jobDir);
            return;
        }
        if ("RUNNING".equals(workerStatus) || statusSnapshot.running()) {
            if (!STATUS_RUNNING.equals(job.status)) {
                job.status = STATUS_RUNNING;
            }
            if (job.startedAt == null) {
                job.startedAt = LocalDateTime.now();
            }
            return;
        }
        if (statusSnapshot.ready()) {
            if (!STATUS_QUEUED.equals(job.status)) {
                job.status = STATUS_QUEUED;
            }
            if (job.queuedAt == null) {
                job.queuedAt = LocalDateTime.now();
            }
        }
    }

    private void transitionToCompleted(LyricAlignmentJob job, String workerOutcome, Path jobDir) {
        job.status = STATUS_COMPLETED;
        job.reviewStatus = REVIEW_PENDING;
        job.workerOutcome = workerOutcome;
        if (job.completedAt == null) {
            job.completedAt = LocalDateTime.now();
        }
        applyResultSnapshot(job, jobDir, workerOutcome);
    }

    private void transitionToFailed(LyricAlignmentJob job, String workerOutcome, Path jobDir) {
        job.status = STATUS_FAILED;
        job.workerOutcome = workerOutcome;
        applyWorkerError(job);
        // Failed worker jobs do not produce reviewable alignment results; keep reviewStatus unchanged.
        if (job.failedAt == null) {
            job.failedAt = LocalDateTime.now();
        }
        applyResultSnapshot(job, jobDir, workerOutcome);
    }

    private void transitionToAbandoned(LyricAlignmentJob job, Path jobDir) {
        job.status = STATUS_ABANDONED;
        job.workerOutcome = "ABANDONED";
        applyResultSnapshot(job, jobDir, "ABANDONED");
    }

    private void applyResultSnapshot(LyricAlignmentJob job, Path jobDir, String workerOutcome) {
        LyricAlignmentResultReader.ResultSnapshot resultSnapshot = resultReader.read(jobDir, workerOutcome);
        job.resultAvailable = resultSnapshot.resultAvailable();
        job.resultSummaryJson = writeJson(resultSnapshot.resultSummary());
        job.alignmentJsonHash = resultSnapshot.alignmentJsonHash();
        job.lrcHash = resultSnapshot.lrcHash();
        job.swlrcHash = resultSnapshot.swlrcHash();
        job.reportHash = resultSnapshot.reportHash();
        if (resultSnapshot.syncMessage() != null) {
            job.syncMessage = resultSnapshot.syncMessage();
        }
    }

    private void applyWorkerError(LyricAlignmentJob job) {
        if (job.workerStatusJson == null || job.workerStatusJson.isBlank()) {
            return;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode statusJson = objectMapper.readTree(job.workerStatusJson);
            com.fasterxml.jackson.databind.JsonNode error = statusJson.get("error");
            if (error == null || !error.isObject()) {
                return;
            }
            String code = textValue(error.get("code"));
            String message = textValue(error.get("message"));
            if (message != null && code != null) {
                job.errorMessage = code + ": " + message;
            } else if (message != null) {
                job.errorMessage = message;
            } else if (code != null) {
                job.errorMessage = code;
            }
        } catch (JsonProcessingException exception) {
            LOG.warnf(exception, "Failed to parse worker error JSON: jobId=%s", job.id);
        }
    }

    private String textValue(com.fasterxml.jackson.databind.JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private String writeJson(com.fasterxml.jackson.databind.JsonNode json) {
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize alignment result summary", exception);
        }
    }

    private String normalizeWorkerStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }
}
