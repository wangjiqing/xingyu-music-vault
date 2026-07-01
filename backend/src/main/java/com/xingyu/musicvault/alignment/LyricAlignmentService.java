package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.CreateAlignmentJobRequest;
import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.LyricRepository;
import com.xingyu.musicvault.lyrics.SongLyric;
import com.xingyu.musicvault.lyrics.SongLyricRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@ApplicationScoped
public class LyricAlignmentService {
    private static final Logger LOG = Logger.getLogger(LyricAlignmentService.class);
    private static final String STATUS_CREATING = "CREATING";
    private static final String STATUS_QUEUED = "QUEUED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REVIEW_NOT_AVAILABLE = "NOT_AVAILABLE";
    private static final String IMPORT_NOT_IMPORTED = "NOT_IMPORTED";

    @Inject
    MusicVaultConfig config;

    @Inject
    LyricRepository lyricRepository;

    @Inject
    SongLyricRepository songLyricRepository;

    @Inject
    LyricAlignmentJobRepository jobRepository;

    @Inject
    LyricAlignmentJobWriter jobWriter;

    @Inject
    ObjectMapper objectMapper;

    public AlignmentJobResponse create(CreateAlignmentJobRequest request) {
        if (request == null || request.songId() == null) {
            throw new BadRequestException("songId is required");
        }
        String createdBy = normalizeCreatedBy(request.createdBy());
        TrackFile trackFile = TrackFile.findById(request.songId());
        if (trackFile == null) {
            throw new NotFoundException("Song not found");
        }
        validateAudioFilePath(trackFile.filePath);
        SongLyric binding = songLyricRepository.findPrimaryBySongId(request.songId());
        if (binding == null) {
            throw new BadRequestException("Trusted lyric not found");
        }
        Lyric lyric = lyricRepository.findById(binding.lyricId);
        if (lyric == null || lyric.content == null || lyric.content.isBlank()) {
            throw new BadRequestException("Trusted lyric not found");
        }

        Path jobsRoot = resolveWritableJobsRoot();
        AudioMapping audioMapping = mapAudioPath(trackFile.filePath);
        String jobId = UUID.randomUUID().toString();
        Path jobDir = jobsRoot.resolve(jobId).normalize();
        if (!jobDir.startsWith(jobsRoot)) {
            throw new BadRequestException("Invalid alignment job id");
        }
        String trustedLyricsHash = sha256(lyric.content);
        ObjectNode requestSnapshot = requestSnapshot(jobId, trackFile, binding.lyricId, audioMapping, trustedLyricsHash, createdBy, request);
        String requestSnapshotJson = writeJson(requestSnapshot);

        LyricAlignmentJob job = QuarkusTransaction.requiringNew().call(() -> {
            LyricAlignmentJob value = new LyricAlignmentJob();
            value.id = jobId;
            value.songId = request.songId();
            value.lyricId = binding.lyricId;
            value.status = STATUS_CREATING;
            value.reviewStatus = REVIEW_NOT_AVAILABLE;
            value.importStatus = IMPORT_NOT_IMPORTED;
            value.audioRelativePath = audioMapping.relativePath();
            value.workerAudioPath = audioMapping.workerPath();
            value.trustedLyricsHash = trustedLyricsHash;
            value.trustedLyricsSnapshot = lyric.content;
            value.requestSnapshotJson = requestSnapshotJson;
            value.jobDir = jobDir.toString();
            value.createdBy = createdBy;
            value.persist();
            return value;
        });

        try {
            jobWriter.writeInputs(jobDir, requestSnapshot, lyric.content, request.sections());
            QuarkusTransaction.requiringNew().run(() -> {
                LyricAlignmentJob queuedJob = jobRepository.findById(jobId);
                queuedJob.status = STATUS_QUEUED;
                queuedJob.queuedAt = LocalDateTime.now();
            });
            jobWriter.markReady(jobDir);
        } catch (IOException | RuntimeException exception) {
            LOG.errorf(exception, "Failed to create alignment job: jobId=%s songId=%d", jobId, request.songId());
            cleanupPartialJobDirectory(jobsRoot, jobDir);
            markFailed(jobId, exception.getMessage());
        }

        return get(jobId);
    }

    public PageResponse<AlignmentJobResponse> list(Integer page, Integer size, String status) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);
        String normalizedStatus = normalizeStatus(status);
        PanacheQuery<LyricAlignmentJob> query = normalizedStatus == null
                ? jobRepository.findAll(Sort.descending("createdAt"))
                : jobRepository.find("status = ?1", Sort.descending("createdAt"), normalizedStatus);
        long total = query.count();
        List<AlignmentJobResponse> items = query.page(Page.of(pageValue, sizeValue)).list().stream()
                .map(this::toResponse)
                .toList();
        return new PageResponse<>(items, pageValue, sizeValue, total);
    }

    public AlignmentJobResponse get(String id) {
        LyricAlignmentJob job = jobRepository.findById(id);
        if (job == null) {
            throw new NotFoundException("Alignment job not found");
        }
        return toResponse(job);
    }

    private void markFailed(String jobId, String message) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                LyricAlignmentJob failedJob = jobRepository.findById(jobId);
                if (failedJob == null) {
                    return;
                }
                failedJob.status = STATUS_FAILED;
                failedJob.errorMessage = message == null || message.isBlank() ? "Failed to create alignment job" : message;
                failedJob.failedAt = LocalDateTime.now();
            });
        } catch (RuntimeException exception) {
            LOG.errorf(exception, "Failed to mark alignment job as FAILED: jobId=%s", jobId);
        }
    }

    private void cleanupPartialJobDirectory(Path jobsRoot, Path jobDir) {
        Path normalizedJobDir = jobDir.toAbsolutePath().normalize();
        if (!normalizedJobDir.startsWith(jobsRoot) || normalizedJobDir.equals(jobsRoot) || !Files.exists(normalizedJobDir)) {
            return;
        }
        try (var paths = Files.walk(normalizedJobDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to clean partial alignment job directory: jobDir=%s", normalizedJobDir);
        }
    }

    private void validateAudioFilePath(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new BadRequestException("Audio file path is required");
        }
        Path audioPath = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.isRegularFile(audioPath)) {
            throw new BadRequestException("Audio file not found");
        }
    }

    private Path resolveWritableJobsRoot() {
        String configured = config.alignmentJobsDir();
        if (configured == null || configured.isBlank()) {
            throw new BadRequestException("Alignment job directory is not configured");
        }
        Path root = Path.of(configured).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            throw new BadRequestException("Alignment job directory does not exist");
        }
        if (!Files.isWritable(root)) {
            throw new BadRequestException("Alignment job directory is not writable");
        }
        return root;
    }

    private AudioMapping mapAudioPath(String filePath) {
        Path audioPath = Path.of(filePath).toAbsolutePath().normalize();
        List<Path> roots = config.musicDirs().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> Path.of(value).toAbsolutePath().normalize())
                .toList();
        for (Path root : roots) {
            if (!audioPath.startsWith(root)) {
                continue;
            }
            Path relative = root.relativize(audioPath).normalize();
            if (relative.isAbsolute() || relative.startsWith("..")) {
                throw new BadRequestException("Audio path is outside the configured music directory");
            }
            String relativeValue = relative.toString();
            if (relativeValue.isBlank()) {
                throw new BadRequestException("Audio relative path is invalid");
            }
            String workerRoot = config.alignmentWorkerMusicDir();
            if (workerRoot == null || workerRoot.isBlank()) {
                throw new BadRequestException("Alignment worker music directory is not configured");
            }
            String workerPath = Path.of(workerRoot).resolve(relative).normalize().toString();
            return new AudioMapping(relativeValue, workerPath);
        }
        throw new BadRequestException("Audio path is not mapped to the worker music directory");
    }

    private ObjectNode requestSnapshot(
            String jobId,
            TrackFile trackFile,
            Long lyricId,
            AudioMapping audioMapping,
            String trustedLyricsHash,
            String createdBy,
            CreateAlignmentJobRequest request
    ) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("jobId", jobId);
        node.put("songId", trackFile.id);
        node.put("lyricId", lyricId);
        node.put("audioRelativePath", audioMapping.relativePath());
        node.put("workerAudioPath", audioMapping.workerPath());
        node.put("trustedLyricsHash", trustedLyricsHash);
        node.put("trustedLyricsFile", "trusted-lyrics.txt");
        node.put("sectionsFile", request.sections() == null || request.sections().isNull() ? null : "sections.json");
        node.put("createdBy", createdBy);
        node.put("createdAt", LocalDateTime.now().toString());
        if (request.workerOptions() != null && !request.workerOptions().isNull()) {
            node.set("workerOptions", request.workerOptions());
        }
        return node;
    }

    private AlignmentJobResponse toResponse(LyricAlignmentJob job) {
        return new AlignmentJobResponse(
                job.id,
                job.songId,
                job.lyricId,
                job.status,
                job.reviewStatus,
                job.importStatus,
                job.audioRelativePath,
                job.workerAudioPath,
                job.trustedLyricsHash,
                job.trustedLyricsSnapshot,
                readJson(job.requestSnapshotJson),
                job.errorMessage,
                readJson(job.resultSummaryJson),
                job.createdBy,
                job.createdAt,
                job.updatedAt,
                job.queuedAt,
                job.startedAt,
                job.completedAt,
                job.failedAt
        );
    }

    private JsonNode readJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize alignment job JSON", exception);
        }
    }

    private String writeJson(JsonNode json) {
        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize alignment job JSON", exception);
        }
    }

    private String normalizeCreatedBy(String createdBy) {
        if (createdBy == null || createdBy.isBlank()) {
            return "admin";
        }
        return createdBy.trim();
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!List.of("CREATING", "QUEUED", "RUNNING", "COMPLETED", "FAILED", "ABANDONED").contains(normalized)) {
            throw new BadRequestException("status must be CREATING, QUEUED, RUNNING, COMPLETED, FAILED, or ABANDONED");
        }
        return normalized;
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new BadRequestException("page must be greater than or equal to 0");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        return size;
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record AudioMapping(String relativePath, String workerPath) {
    }
}
