package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class LyricAlignmentWorkerStatusReader {
    private static final Logger LOG = Logger.getLogger(LyricAlignmentWorkerStatusReader.class);

    private static final String READY = "READY";
    private static final String RUNNING = "RUNNING";
    private static final String SUCCEEDED = "SUCCEEDED";
    private static final String NEEDS_REVIEW = "NEEDS_REVIEW";
    private static final String FAILED = "FAILED";
    private static final String ABANDONED = "ABANDONED";
    private static final String STATUS_JSON = "status.json";

    @Inject
    ObjectMapper objectMapper;

    public WorkerStatusSnapshot read(Path jobDir) {
        if (jobDir == null || !Files.isDirectory(jobDir)) {
            return new WorkerStatusSnapshot(false, false, false, false, false, false, null, null, null, "Alignment job directory is missing");
        }

        JsonNode statusJson = null;
        String statusJsonRaw = null;
        String status = null;
        String syncMessage = null;
        Path statusPath = jobDir.resolve(STATUS_JSON);
        if (Files.isRegularFile(statusPath)) {
            try {
                statusJsonRaw = Files.readString(statusPath, StandardCharsets.UTF_8);
                statusJson = objectMapper.readTree(statusJsonRaw);
                JsonNode schemaVersionNode = statusJson.get("schemaVersion");
                if (schemaVersionNode != null && schemaVersionNode.canConvertToInt()
                        && schemaVersionNode.asInt() != 1) {
                    syncMessage = "Unsupported worker status schemaVersion: " + schemaVersionNode.asInt();
                } else {
                    JsonNode statusNode = statusJson.get("status");
                    if (statusNode != null && statusNode.isTextual()) {
                        status = statusNode.asText();
                    }
                }
            } catch (IOException | RuntimeException exception) {
                LOG.warnf(exception, "Failed to read alignment worker status JSON: jobDir=%s", jobDir);
                statusJsonRaw = null;
                syncMessage = "Worker status JSON is not readable yet";
            }
        }

        return new WorkerStatusSnapshot(
                Files.exists(jobDir.resolve(READY)),
                Files.exists(jobDir.resolve(RUNNING)),
                Files.exists(jobDir.resolve(SUCCEEDED)),
                Files.exists(jobDir.resolve(NEEDS_REVIEW)),
                Files.exists(jobDir.resolve(FAILED)),
                Files.exists(jobDir.resolve(ABANDONED)),
                status,
                statusJson,
                statusJsonRaw,
                syncMessage
        );
    }

    public record WorkerStatusSnapshot(
            boolean ready,
            boolean running,
            boolean succeeded,
            boolean needsReview,
            boolean failed,
            boolean abandoned,
            String status,
            JsonNode statusJson,
            String statusJsonRaw,
            String syncMessage
    ) {
    }
}
