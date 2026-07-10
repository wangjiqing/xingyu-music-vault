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
import java.util.ArrayList;
import java.util.List;

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
            return new WorkerStatusSnapshot(
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    false,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "Alignment job directory is missing",
                    List.of("任务目录不存在")
            );
        }

        JsonNode statusJson = null;
        String statusJsonRaw = null;
        String status = null;
        Integer schemaVersion = null;
        Integer statusSchemaVersion = null;
        Integer requestSchemaVersion = null;
        String jobId = null;
        String taskType = null;
        String stage = null;
        String startedAt = null;
        String stageStartedAt = null;
        String updatedAt = null;
        String heartbeatAt = null;
        JsonNode progress = null;
        JsonNode attempt = null;
        JsonNode requestedConfig = null;
        JsonNode resolvedConfig = null;
        JsonNode warnings = null;
        JsonNode error = null;
        JsonNode result = null;
        String syncMessage = null;
        List<String> compatibilityMessages = new ArrayList<>();
        Path statusPath = jobDir.resolve(STATUS_JSON);
        if (Files.isRegularFile(statusPath)) {
            try {
                statusJsonRaw = Files.readString(statusPath, StandardCharsets.UTF_8);
                statusJson = objectMapper.readTree(statusJsonRaw);
                schemaVersion = intValue(statusJson.get("schemaVersion"));
                statusSchemaVersion = intValue(statusJson.get("statusSchemaVersion"));
                requestSchemaVersion = intValue(statusJson.get("requestSchemaVersion"));
                if (statusSchemaVersion == null && schemaVersion == null) {
                    compatibilityMessages.add("status.json 未声明协议版本，按旧协议宽容读取");
                }
                if (schemaVersion != null && schemaVersion != 1 && schemaVersion != 2) {
                    compatibilityMessages.add("未知旧 status schemaVersion: " + schemaVersion + "，已按可识别字段宽容读取");
                }
                if (statusSchemaVersion != null && statusSchemaVersion != 1) {
                    compatibilityMessages.add("未知 statusSchemaVersion: " + statusSchemaVersion + "，已按可识别字段宽容读取");
                }
                jobId = textValue(statusJson.get("jobId"));
                taskType = textValue(statusJson.get("taskType"));
                status = firstTextValue(statusJson.get("state"), statusJson.get("status"));
                stage = textValue(statusJson.get("stage"));
                startedAt = textValue(statusJson.get("startedAt"));
                stageStartedAt = textValue(statusJson.get("stageStartedAt"));
                updatedAt = textValue(statusJson.get("updatedAt"));
                heartbeatAt = textValue(statusJson.get("heartbeatAt"));
                progress = objectOrArray(statusJson.get("progress"));
                attempt = objectOrArray(statusJson.get("attempt"));
                requestedConfig = objectOrArray(statusJson.get("requestedConfig"));
                resolvedConfig = objectOrArray(statusJson.get("resolvedConfig"));
                warnings = objectOrArray(statusJson.get("warnings"));
                error = objectOrArray(statusJson.get("error"));
                result = objectOrArray(statusJson.get("result"));
            } catch (IOException | RuntimeException exception) {
                LOG.warnf(exception, "Failed to read alignment worker status JSON: jobDir=%s", jobDir);
                statusJsonRaw = null;
                syncMessage = "Worker status JSON is not readable yet";
                compatibilityMessages.add(syncMessage);
            }
        }

        return new WorkerStatusSnapshot(
                true,
                Files.exists(jobDir.resolve(READY)),
                Files.exists(jobDir.resolve(RUNNING)),
                Files.exists(jobDir.resolve(SUCCEEDED)),
                Files.exists(jobDir.resolve(NEEDS_REVIEW)),
                Files.exists(jobDir.resolve(FAILED)),
                Files.exists(jobDir.resolve(ABANDONED)),
                Files.isRegularFile(statusPath),
                status,
                schemaVersion,
                statusSchemaVersion,
                requestSchemaVersion,
                jobId,
                taskType,
                stage,
                startedAt,
                stageStartedAt,
                updatedAt,
                heartbeatAt,
                progress,
                attempt,
                requestedConfig,
                resolvedConfig,
                warnings,
                error,
                result,
                statusJson,
                statusJsonRaw,
                syncMessage,
                compatibilityMessages
        );
    }

    private Integer intValue(JsonNode node) {
        return node != null && node.canConvertToInt() ? node.asInt() : null;
    }

    private String firstTextValue(JsonNode first, JsonNode second) {
        String value = textValue(first);
        return value == null ? textValue(second) : value;
    }

    private String textValue(JsonNode node) {
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private JsonNode objectOrArray(JsonNode node) {
        return node != null && (node.isObject() || node.isArray()) ? node : null;
    }

    public record WorkerStatusSnapshot(
            boolean directoryAvailable,
            boolean ready,
            boolean running,
            boolean succeeded,
            boolean needsReview,
            boolean failed,
            boolean abandoned,
            boolean statusAvailable,
            String status,
            Integer schemaVersion,
            Integer statusSchemaVersion,
            Integer requestSchemaVersion,
            String jobId,
            String taskType,
            String stage,
            String startedAt,
            String stageStartedAt,
            String updatedAt,
            String heartbeatAt,
            JsonNode progress,
            JsonNode attempt,
            JsonNode requestedConfig,
            JsonNode resolvedConfig,
            JsonNode warnings,
            JsonNode error,
            JsonNode result,
            JsonNode statusJson,
            String statusJsonRaw,
            String syncMessage,
            List<String> compatibilityMessages
    ) {
    }
}
