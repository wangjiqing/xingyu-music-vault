package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ApplicationScoped
public class LyricAlignmentResultReader {
    private static final Logger LOG = Logger.getLogger(LyricAlignmentResultReader.class);

    static final String RESULT_DIR = "result";
    static final String ALIGNMENT_JSON = "alignment.json";
    static final String LYRICS_LRC = "lyrics.lrc";
    static final String LYRICS_SWLRC = "lyrics.swlrc";
    static final String REPORT_JSON = "report.json";

    @Inject
    ObjectMapper objectMapper;

    public ResultSnapshot read(Path jobDir, String workerOutcome) {
        Path resultDir = jobDir.resolve(RESULT_DIR).normalize();
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("workerOutcome", workerOutcome);
        addWorkerResultSummary(summary, jobDir);
        if (!Files.isDirectory(resultDir)) {
            summary.put("resultDirectoryAvailable", false);
            summary.put("alignmentJsonAvailable", false);
            summary.put("lrcAvailable", false);
            summary.put("swlrcAvailable", false);
            summary.put("reportAvailable", false);
            return new ResultSnapshot(false, summary, null, null, null, null, "Alignment result directory is not available yet");
        }

        Path alignmentPath = resultDir.resolve(ALIGNMENT_JSON);
        Path lrcPath = resultDir.resolve(LYRICS_LRC);
        Path swlrcPath = resultDir.resolve(LYRICS_SWLRC);
        Path reportPath = resultDir.resolve(REPORT_JSON);
        boolean alignmentAvailable = Files.isRegularFile(alignmentPath);
        boolean lrcAvailable = Files.isRegularFile(lrcPath);
        boolean swlrcAvailable = Files.isRegularFile(swlrcPath);
        boolean reportAvailable = Files.isRegularFile(reportPath);

        summary.put("resultDirectoryAvailable", true);
        summary.put("alignmentJsonAvailable", alignmentAvailable);
        summary.put("lrcAvailable", lrcAvailable);
        summary.put("swlrcAvailable", swlrcAvailable);
        summary.put("reportAvailable", reportAvailable);
        summary.put("reportJsonReadable", readableJson(reportPath));
        addReportSummary(summary, reportPath);

        String syncMessage = null;
        try {
            return new ResultSnapshot(
                    alignmentAvailable || lrcAvailable || swlrcAvailable || reportAvailable,
                    summary,
                    hashIfPresent(alignmentPath),
                    hashIfPresent(lrcPath),
                    hashIfPresent(swlrcPath),
                    hashIfPresent(reportPath),
                    syncMessage
            );
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to read alignment result files: resultDir=%s", resultDir);
            return new ResultSnapshot(
                    alignmentAvailable || lrcAvailable || swlrcAvailable || reportAvailable,
                    summary,
                    null,
                    null,
                    null,
                    null,
                    "Alignment result files are not fully readable yet"
            );
        }
    }

    private boolean readableJson(Path path) {
        if (!Files.isRegularFile(path)) {
            return false;
        }
        try {
            objectMapper.readTree(path.toFile());
            return true;
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "Failed to parse alignment report JSON: path=%s", path);
            return false;
        }
    }

    private void addWorkerResultSummary(ObjectNode summary, Path jobDir) {
        Path statusPath = jobDir.resolve("status.json");
        if (!Files.isRegularFile(statusPath)) {
            return;
        }
        try {
            JsonNode statusJson = objectMapper.readTree(statusPath.toFile());
            JsonNode status = statusJson.get("status");
            if (status != null && status.isTextual()) {
                summary.put("workerStatus", status.asText());
            }
            JsonNode result = statusJson.get("result");
            if (result == null || !result.isObject()) {
                return;
            }
            JsonNode resultSummary = result.get("summary");
            if (resultSummary != null && resultSummary.isObject()) {
                copyNumber(summary, resultSummary, "line_count", "lineCount");
                copyNumber(summary, resultSummary, "aligned_line_count", "alignedLineCount");
                copyNumber(summary, resultSummary, "token_count", "tokenCount");
                copyNumber(summary, resultSummary, "coverage", "coverage");
                copyNumber(summary, resultSummary, "estimated_token_count", "estimatedTokenCount");
                copyNumber(summary, resultSummary, "skipped_line_count", "skippedLineCount");
            }
            JsonNode warnings = result.get("warnings");
            if (warnings != null && warnings.isArray()) {
                summary.put("warningCount", warnings.size());
            }
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "Failed to summarize worker status result: jobDir=%s", jobDir);
        }
    }

    private void addReportSummary(ObjectNode summary, Path reportPath) {
        if (!Files.isRegularFile(reportPath)) {
            return;
        }
        try {
            JsonNode report = objectMapper.readTree(reportPath.toFile());
            copyNumber(summary, report, "line_count", "reportLineCount");
            copyNumber(summary, report, "aligned_or_partial_lines", "reportAlignedOrPartialLines");
            copyNumber(summary, report, "missing_character_timestamps", "missingCharacterTimestamps");
            copyNumber(summary, report, "non_monotonic_line_count", "nonMonotonicLineCount");
            copyNumber(summary, report, "estimated_token_count", "reportEstimatedTokenCount");
            copyNumber(summary, report, "skipped_line_count", "reportSkippedLineCount");
            JsonNode warnings = report.get("warnings");
            if (warnings != null && warnings.isArray()) {
                summary.put("reportWarningCount", warnings.size());
            }
            JsonNode swlrcWarnings = report.get("swlrc_warnings");
            if (swlrcWarnings != null && swlrcWarnings.isArray()) {
                summary.put("swlrcWarningCount", swlrcWarnings.size());
            }
            JsonNode statusCounts = report.get("status_counts");
            if (statusCounts != null && statusCounts.isObject()) {
                summary.set("statusCounts", statusCounts);
            }
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "Failed to summarize alignment report JSON: path=%s", reportPath);
        }
    }

    private void copyNumber(ObjectNode target, JsonNode source, String sourceName, String targetName) {
        JsonNode value = source.get(sourceName);
        if (value == null || !value.isNumber()) {
            return;
        }
        target.set(targetName, value);
    }

    private String hashIfPresent(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return sha256(Files.readAllBytes(path));
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record ResultSnapshot(
            boolean resultAvailable,
            JsonNode resultSummary,
            String alignmentJsonHash,
            String lrcHash,
            String swlrcHash,
            String reportHash,
            String syncMessage
    ) {
    }
}
