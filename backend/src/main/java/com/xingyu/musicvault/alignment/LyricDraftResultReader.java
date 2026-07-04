package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xingyu.musicvault.config.MusicVaultConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ApplicationScoped
public class LyricDraftResultReader {
    private static final Logger LOG = Logger.getLogger(LyricDraftResultReader.class);

    static final String RESULT_DIR = "result";
    static final String TRANSCRIPT_CLEANED = "transcript.cleaned.txt";
    static final String TRANSCRIPT_RAW = "transcript.raw.txt";
    static final String TRANSCRIPT_SEGMENTS = "transcript.segments.json";
    static final String REPORT_JSON = "report.json";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    MusicVaultConfig config;

    public DraftResultSnapshot read(Path jobDir) {
        Path resultDir = jobDir.resolve(RESULT_DIR).normalize();
        ObjectNode summary = objectMapper.createObjectNode();
        summary.put("taskType", "LYRIC_DRAFT_EXTRACTION");
        if (!Files.isDirectory(resultDir)) {
            summary.put("resultDirectoryAvailable", false);
            return new DraftResultSnapshot(false, null, summary, null, null, null,
                    "Draft result directory is not available yet");
        }

        Path cleanedPath = resultDir.resolve(TRANSCRIPT_CLEANED).normalize();
        Path rawPath = resultDir.resolve(TRANSCRIPT_RAW).normalize();
        Path segmentsPath = resultDir.resolve(TRANSCRIPT_SEGMENTS).normalize();
        Path reportPath = resultDir.resolve(REPORT_JSON).normalize();
        boolean cleanedAvailable = Files.isRegularFile(cleanedPath);
        boolean rawAvailable = Files.isRegularFile(rawPath);
        boolean segmentsAvailable = Files.isRegularFile(segmentsPath);
        boolean reportAvailable = Files.isRegularFile(reportPath);

        summary.put("resultDirectoryAvailable", true);
        summary.put("cleanedTranscriptAvailable", cleanedAvailable);
        summary.put("rawTranscriptAvailable", rawAvailable);
        summary.put("segmentsAvailable", segmentsAvailable);
        summary.put("reportAvailable", reportAvailable);
        summary.put("segmentsJsonReadable", readableJson(segmentsPath));
        summary.put("reportJsonReadable", readableJson(reportPath));
        addReportSummary(summary, reportPath);

        if (!cleanedAvailable) {
            return new DraftResultSnapshot(false, null, summary, null, null, null,
                    "Cleaned transcript is not available");
        }

        try {
            long maxBytes = Math.max(1024, config.alignmentDraftMaxTextBytes());
            long size = Files.size(cleanedPath);
            if (size > maxBytes) {
                return new DraftResultSnapshot(false, null, summary, null, null, null,
                        "Cleaned transcript exceeds maximum allowed size");
            }
            String cleanedText = stripBom(Files.readString(cleanedPath, StandardCharsets.UTF_8));
            if (cleanedText.isBlank()) {
                return new DraftResultSnapshot(false, null, summary, null, null, null,
                        "Cleaned transcript is empty");
            }
            summary.put("textBytes", size);
            summary.put("lineCount", cleanedText.lines().count());
            return new DraftResultSnapshot(
                    true,
                    cleanedText,
                    summary,
                    hashIfPresent(rawPath),
                    hashIfPresent(segmentsPath),
                    hashIfPresent(reportPath),
                    null
            );
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "Failed to read lyric draft result files: resultDir=%s", resultDir);
            return new DraftResultSnapshot(false, null, summary, null, null, null,
                    "Draft result files are not fully readable yet");
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
            LOG.warnf(exception, "Failed to parse lyric draft JSON artifact: path=%s", path);
            return false;
        }
    }

    private void addReportSummary(ObjectNode summary, Path reportPath) {
        if (!Files.isRegularFile(reportPath)) {
            return;
        }
        try {
            JsonNode report = objectMapper.readTree(reportPath.toFile());
            copyNumber(summary, report, "duration_seconds", "durationSeconds");
            copyNumber(summary, report, "segment_count", "segmentCount");
            copyNumber(summary, report, "line_count", "reportLineCount");
            JsonNode warnings = report.get("warnings");
            if (warnings != null && warnings.isArray()) {
                summary.put("warningCount", warnings.size());
            }
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "Failed to summarize lyric draft report JSON: path=%s", reportPath);
        }
    }

    private void copyNumber(ObjectNode target, JsonNode source, String sourceName, String targetName) {
        JsonNode value = source.get(sourceName);
        if (value != null && value.isNumber()) {
            target.set(targetName, value);
        }
    }

    private String hashIfPresent(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return null;
        }
        return sha256(Files.readAllBytes(path));
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
    }

    private String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record DraftResultSnapshot(
            boolean draftAvailable,
            String cleanedText,
            JsonNode reportSummary,
            String transcriptRawHash,
            String transcriptSegmentsHash,
            String reportHash,
            String syncMessage
    ) {
    }
}
