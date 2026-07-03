package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobListItemResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ArtifactContent;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.CreateAlignmentJobRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ImportAlignmentJobRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ImportAlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ReviewAlignmentJobRequest;
import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.LyricRepository;
import com.xingyu.musicvault.lyrics.SongLyric;
import com.xingyu.musicvault.lyrics.SongLyricRepository;
import com.xingyu.musicvault.openapi.OpenApiChangeLogService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String REVIEW_NOT_AVAILABLE = "NOT_AVAILABLE";
    private static final String REVIEW_PENDING = "PENDING";
    private static final String REVIEW_APPROVED = "APPROVED";
    private static final String REVIEW_REJECTED = "REJECTED";
    private static final String IMPORT_NOT_IMPORTED = "NOT_IMPORTED";
    private static final String IMPORT_IMPORTED = "IMPORTED";
    private static final String IMPORT_FAILED = "IMPORT_FAILED";
    private static final String SOURCE_ALIGNMENT = "ALIGNMENT";
    private static final String TEXT_PLAIN_UTF8 = "text/plain; charset=UTF-8";
    private static final String APPLICATION_JSON_UTF8 = "application/json; charset=UTF-8";

    @Inject
    MusicVaultConfig config;

    @Inject
    LyricRepository lyricRepository;

    @Inject
    SongLyricRepository songLyricRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    LyricAlignmentJobRepository jobRepository;

    @Inject
    LyricAlignmentJobWriter jobWriter;

    @Inject
    OpenApiChangeLogService openApiChangeLogService;

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

    public PageResponse<AlignmentJobListItemResponse> list(Integer page, Integer size, String status) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);
        String normalizedStatus = normalizeStatus(status);
        PanacheQuery<LyricAlignmentJob> query = normalizedStatus == null
                ? jobRepository.findAll(Sort.descending("createdAt"))
                : jobRepository.find("status = ?1", Sort.descending("createdAt"), normalizedStatus);
        long total = query.count();
        List<AlignmentJobListItemResponse> items = query.page(Page.of(pageValue, sizeValue)).list().stream()
                .map(this::toListItemResponse)
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

    public ArtifactContent getArtifact(String id, AlignmentArtifact artifact) {
        LyricAlignmentJob job = jobRepository.findById(id);
        if (job == null) {
            throw new NotFoundException("Alignment job not found");
        }
        Path jobsRoot = Path.of(config.alignmentJobsDir()).toAbsolutePath().normalize();
        Path jobDir = Path.of(job.jobDir).toAbsolutePath().normalize();
        if (!jobDir.startsWith(jobsRoot) || jobDir.equals(jobsRoot)) {
            throw new BadRequestException("Alignment job directory is invalid");
        }
        Path artifactPath = jobDir.resolve(artifact.relativePath()).normalize();
        if (!artifactPath.startsWith(jobDir)) {
            throw new BadRequestException("Alignment artifact path is invalid");
        }
        if (!Files.isRegularFile(artifactPath)) {
            throw new NotFoundException("Alignment artifact not found");
        }
        try {
            return new ArtifactContent(artifact.fileName(), artifact.mediaType(), Files.readAllBytes(artifactPath));
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to read alignment artifact: jobId=%s artifact=%s", id, artifact.fileName());
            throw new BadRequestException("Alignment artifact is not readable");
        }
    }

    public AlignmentJobResponse approve(String id, ReviewAlignmentJobRequest request) {
        return review(id, request, REVIEW_APPROVED, "APPROVED");
    }

    public AlignmentJobResponse reject(String id, ReviewAlignmentJobRequest request) {
        return review(id, request, REVIEW_REJECTED, "REJECTED");
    }

    public ImportAlignmentJobResponse importApproved(String id, ImportAlignmentJobRequest request) {
        String operator = normalizeOperator(request == null ? null : request.importedBy());
        LyricAlignmentJob job = jobRepository.findById(id);
        if (job == null) {
            throw new NotFoundException("Alignment job not found");
        }
        if (IMPORT_IMPORTED.equals(job.importStatus) && job.importedLyricId != null) {
            Lyric imported = lyricRepository.findById(job.importedLyricId);
            if (imported != null) {
                return importResponse(job, imported.id);
            }
            throw new ConflictException("Imported lyric record is missing");
        }
        validateImportableStatus(job);

        ImportSource importSource;
        try {
            importSource = validateImportSource(job);
        } catch (RuntimeException exception) {
            markImportFailed(id, operator, exception.getMessage());
            throw exception;
        }

        ImportedFiles importedFiles;
        try {
            importedFiles = copyResultFilesToAssets(job, importSource);
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "Failed to copy alignment result assets: jobId=%s", id);
            markImportFailed(id, operator, exception.getMessage());
            throw new BadRequestException("Failed to import alignment result: " + safeMessage(exception));
        }

        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                LyricAlignmentJob importJob = jobRepository.findById(id);
                validateImportableStatus(importJob);

                Lyric existing = lyricRepository.findAlignmentBySourceTaskId(id);
                Lyric lyric = existing == null ? new Lyric() : existing;
                TrackFile trackFile = TrackFile.findById(importJob.songId);
                if (trackFile == null) {
                    throw new NotFoundException("Song not found");
                }
                Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
                LocalDateTime now = LocalDateTime.now();
                lyric.title = track == null ? stripExtension(trackFile.fileName) : track.title;
                lyric.artist = track == null ? null : track.artist;
                lyric.album = track == null ? null : track.album;
                lyric.language = null;
                lyric.sourceType = SOURCE_ALIGNMENT;
                lyric.sourcePath = importedFiles.lrcPath().toString();
                lyric.content = importedFiles.lrcContent();
                lyric.contentHash = importJob.lrcHash;
                lyric.format = "LRC";
                lyric.parseStatus = "PARSED";
                lyric.parseMessage = null;
                lyric.sourceTaskId = importJob.id;
                lyric.parentLyricsId = importJob.lyricId;
                lyric.swlrcPath = importedFiles.swlrcPath().toString();
                lyric.swlrcHash = importJob.swlrcHash;
                lyric.confirmedAt = now;
                lyric.confirmedBy = operator;
                if (existing == null) {
                    lyric.persist();
                }

                bindImportedLyric(importJob.songId, lyric.id);
                openApiChangeLogService.recordLyricsChange(importJob.songId);

                String beforeImportStatus = importJob.importStatus;
                importJob.importStatus = IMPORT_IMPORTED;
                importJob.importedBy = operator;
                if (importJob.importedAt == null) {
                    importJob.importedAt = now;
                }
                importJob.importedLyricId = lyric.id;
                importJob.importErrorMessage = null;
                recordEvent(importJob, "IMPORTED", operator, null, beforeImportStatus, IMPORT_IMPORTED, null);
                return importResponse(importJob, lyric.id);
            });
        } catch (RuntimeException exception) {
            markImportFailed(id, operator, exception.getMessage());
            throw exception;
        }
    }

    private AlignmentJobResponse review(String id, ReviewAlignmentJobRequest request, String targetStatus, String action) {
        String operator = normalizeOperator(request == null ? null : request.reviewedBy());
        String note = normalizeNote(request == null ? null : request.reviewNote());
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                LyricAlignmentJob job = jobRepository.findById(id);
                if (job == null) {
                    throw new NotFoundException("Alignment job not found");
                }
                if (!STATUS_COMPLETED.equals(job.status)) {
                    throw new ConflictException("Only completed alignment jobs can be reviewed");
                }
                if (!REVIEW_PENDING.equals(job.reviewStatus)) {
                    throw new ConflictException("Alignment job is not pending review");
                }
                String before = job.reviewStatus;
                job.reviewStatus = targetStatus;
                job.reviewedBy = operator;
                job.reviewedAt = LocalDateTime.now();
                job.reviewNote = note;
                recordEvent(job, action, operator, note, before, targetStatus, null);
                return toResponse(job);
            });
        } catch (RuntimeException exception) {
            throw exception;
        }
    }

    private ImportSource validateImportSource(LyricAlignmentJob job) {
        if (job.lrcHash == null || job.lrcHash.isBlank() || job.swlrcHash == null || job.swlrcHash.isBlank()) {
            throw new BadRequestException("Alignment result hashes are not available");
        }
        if (TrackFile.findById(job.songId) == null) {
            throw new NotFoundException("Song not found");
        }

        Path jobDir = safeJobDir(job);
        Path resultDir = jobDir.resolve(LyricAlignmentResultReader.RESULT_DIR).normalize();
        if (!resultDir.startsWith(jobDir) || !Files.isDirectory(resultDir)) {
            throw new BadRequestException("Alignment result directory is not available");
        }
        Path lrcPath = resultDir.resolve(LyricAlignmentResultReader.LYRICS_LRC).normalize();
        Path swlrcPath = resultDir.resolve(LyricAlignmentResultReader.LYRICS_SWLRC).normalize();
        if (!lrcPath.startsWith(resultDir) || !swlrcPath.startsWith(resultDir)) {
            throw new BadRequestException("Alignment result path is invalid");
        }
        if (!Files.isRegularFile(lrcPath)) {
            throw new BadRequestException("Alignment LRC result is not available");
        }
        if (!Files.isRegularFile(swlrcPath)) {
            throw new BadRequestException("Alignment SWLRC result is not available");
        }
        String actualLrcHash = sha256File(lrcPath);
        String actualSwlrcHash = sha256File(swlrcPath);
        if (!job.lrcHash.equals(actualLrcHash)) {
            throw new BadRequestException("Alignment LRC result hash does not match the synchronized hash");
        }
        if (!job.swlrcHash.equals(actualSwlrcHash)) {
            throw new BadRequestException("Alignment SWLRC result hash does not match the synchronized hash");
        }
        return new ImportSource(lrcPath, swlrcPath);
    }

    private void validateImportableStatus(LyricAlignmentJob job) {
        if (job == null) {
            throw new NotFoundException("Alignment job not found");
        }
        if (!STATUS_COMPLETED.equals(job.status)) {
            throw new ConflictException("Only completed alignment jobs can be imported");
        }
        if (!REVIEW_APPROVED.equals(job.reviewStatus)) {
            throw new ConflictException("Only approved alignment jobs can be imported");
        }
        if (!IMPORT_NOT_IMPORTED.equals(job.importStatus) && !IMPORT_FAILED.equals(job.importStatus)) {
            throw new ConflictException("Alignment job cannot be imported in current import status");
        }
    }

    private ImportedFiles copyResultFilesToAssets(LyricAlignmentJob job, ImportSource source) throws IOException {
        Path root = resolveWritableAlignmentAssetsRoot();
        Path targetDir = root.resolve(String.valueOf(job.songId)).resolve(job.id).normalize();
        if (!targetDir.startsWith(root) || targetDir.equals(root)) {
            throw new BadRequestException("Alignment asset directory is invalid");
        }
        Files.createDirectories(targetDir);
        if (!Files.isWritable(targetDir)) {
            throw new BadRequestException("Alignment asset directory is not writable");
        }

        Path targetLrc = targetDir.resolve(LyricAlignmentResultReader.LYRICS_LRC).normalize();
        Path targetSwlrc = targetDir.resolve(LyricAlignmentResultReader.LYRICS_SWLRC).normalize();
        copyVerifiedAsset(source.lrcPath(), targetLrc, job.lrcHash);
        copyVerifiedAsset(source.swlrcPath(), targetSwlrc, job.swlrcHash);
        String lrcContent = Files.readString(targetLrc, StandardCharsets.UTF_8);
        return new ImportedFiles(targetLrc, targetSwlrc, lrcContent);
    }

    private void copyVerifiedAsset(Path source, Path target, String expectedHash) throws IOException {
        if (Files.exists(target) && expectedHash.equals(sha256File(target))) {
            return;
        }
        if (Files.exists(target)) {
            throw new BadRequestException("Alignment asset target already exists with different content");
        }
        Path temp = target.resolveSibling("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
        String copiedHash = sha256File(temp);
        if (!expectedHash.equals(copiedHash)) {
            Files.deleteIfExists(temp);
            throw new BadRequestException("Copied alignment asset hash does not match expected hash");
        }
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            LOG.warnf(exception, "Atomic move is not supported for imported alignment asset; falling back to replace move: target=%s", target);
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Path resolveWritableAlignmentAssetsRoot() throws IOException {
        String configured = config.alignmentAssetsDir();
        if (configured == null || configured.isBlank()) {
            throw new BadRequestException("Alignment asset directory is not configured");
        }
        Path root = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(root);
        if (!Files.isDirectory(root)) {
            throw new BadRequestException("Alignment asset directory does not exist");
        }
        if (!Files.isWritable(root)) {
            throw new BadRequestException("Alignment asset directory is not writable");
        }
        return root;
    }

    private Path safeJobDir(LyricAlignmentJob job) {
        Path jobsRoot = Path.of(config.alignmentJobsDir()).toAbsolutePath().normalize();
        Path jobDir = Path.of(job.jobDir).toAbsolutePath().normalize();
        if (!jobDir.startsWith(jobsRoot) || jobDir.equals(jobsRoot)) {
            throw new BadRequestException("Alignment job directory is invalid");
        }
        return jobDir;
    }

    private void bindImportedLyric(Long songId, Long lyricId) {
        SongLyric existing = songLyricRepository.findBySongIdAndLyricId(songId, lyricId);
        SongLyric.update("isPrimary = false where songId = ?1 and lyricId <> ?2", songId, lyricId);
        entityManager.flush();
        if (existing != null) {
            existing.isPrimary = true;
            existing.matchType = "ALIGNMENT_APPROVED";
            existing.matchScore = 100;
            return;
        }
        SongLyric binding = new SongLyric();
        binding.songId = songId;
        binding.lyricId = lyricId;
        binding.matchType = "ALIGNMENT_APPROVED";
        binding.matchScore = 100;
        binding.isPrimary = true;
        binding.persist();
    }

    private void markImportFailed(String jobId, String operator, String message) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                LyricAlignmentJob job = jobRepository.findById(jobId);
                if (job == null || IMPORT_IMPORTED.equals(job.importStatus)) {
                    return;
                }
                String before = job.importStatus;
                job.importStatus = IMPORT_FAILED;
                job.importErrorMessage = message == null || message.isBlank() ? "Failed to import alignment result" : message;
                recordEvent(job, "IMPORT_FAILED", operator, null, before, IMPORT_FAILED, job.importErrorMessage);
            });
        } catch (RuntimeException exception) {
            LOG.errorf(exception, "Failed to mark alignment import as failed: jobId=%s", jobId);
        }
    }

    private void recordEvent(
            LyricAlignmentJob job,
            String action,
            String operator,
            String note,
            String beforeStatus,
            String afterStatus,
            String errorMessage
    ) {
        LyricAlignmentJobEvent event = new LyricAlignmentJobEvent();
        event.taskId = job.id;
        event.musicId = job.songId;
        event.action = action;
        event.operator = operator;
        event.note = note;
        event.beforeStatus = beforeStatus;
        event.afterStatus = afterStatus;
        event.errorMessage = errorMessage;
        event.persist();
    }

    private ImportAlignmentJobResponse importResponse(LyricAlignmentJob job, Long importedLyricId) {
        return new ImportAlignmentJobResponse(
                job.id,
                job.songId,
                job.lyricId,
                importedLyricId,
                job.importStatus,
                job.lrcHash,
                job.swlrcHash,
                job.importedAt,
                job.importedBy
        );
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
        node.put("schemaVersion", 1);
        node.put("jobId", jobId);
        node.put("audioPath", audioMapping.workerPath());
        node.put("lyricsPath", workerJobPath(jobId, "trusted-lyrics.txt"));
        node.put("outputDir", workerJobPath(jobId, "result"));
        if (request.sections() == null || request.sections().isNull()) {
            node.putNull("sectionManifestPath");
        } else {
            node.put("sectionManifestPath", workerJobPath(jobId, "sections.json"));
        }
        node.put("language", workerOptionText(request.workerOptions(), "language", "zh"));
        node.put("device", workerOptionText(request.workerOptions(), "device", "cpu"));
        node.put("createdAt", LocalDateTime.now().toString());
        return node;
    }

    private String workerOptionText(JsonNode workerOptions, String fieldName, String defaultValue) {
        if (workerOptions == null || workerOptions.isNull()) {
            return defaultValue;
        }
        JsonNode value = workerOptions.get(fieldName);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            return defaultValue;
        }
        return value.asText().trim();
    }

    private String workerJobPath(String jobId, String fileName) {
        String workerJobsRoot = config.alignmentWorkerJobsDir();
        if (workerJobsRoot == null || workerJobsRoot.isBlank()) {
            throw new BadRequestException("Alignment worker job directory is not configured");
        }
        return Path.of(workerJobsRoot).resolve(jobId).resolve(fileName).normalize().toString();
    }

    private AlignmentJobResponse toResponse(LyricAlignmentJob job) {
        return new AlignmentJobResponse(
                job.id,
                job.songId,
                job.lyricId,
                job.status,
                job.reviewStatus,
                job.importStatus,
                job.workerOutcome,
                job.audioRelativePath,
                job.workerAudioPath,
                job.trustedLyricsHash,
                job.trustedLyricsSnapshot,
                readJson(job.requestSnapshotJson),
                job.errorMessage,
                readJson(job.resultSummaryJson),
                readJson(job.workerStatusJson),
                job.alignmentJsonHash,
                job.lrcHash,
                job.swlrcHash,
                job.reportHash,
                job.resultAvailable,
                job.syncMessage,
                job.createdBy,
                job.createdAt,
                job.updatedAt,
                job.queuedAt,
                job.startedAt,
                job.completedAt,
                job.failedAt,
                job.reviewedBy,
                job.reviewedAt,
                job.reviewNote,
                job.importedBy,
                job.importedAt,
                job.importErrorMessage,
                job.importedLyricId
        );
    }

    private AlignmentJobListItemResponse toListItemResponse(LyricAlignmentJob job) {
        return new AlignmentJobListItemResponse(
                job.id,
                job.songId,
                job.lyricId,
                job.status,
                job.reviewStatus,
                job.importStatus,
                job.workerOutcome,
                job.audioRelativePath,
                job.trustedLyricsHash,
                job.errorMessage,
                readJson(job.resultSummaryJson),
                job.alignmentJsonHash,
                job.lrcHash,
                job.swlrcHash,
                job.reportHash,
                job.resultAvailable,
                job.syncMessage,
                job.createdBy,
                job.createdAt,
                job.updatedAt,
                job.queuedAt,
                job.startedAt,
                job.completedAt,
                job.failedAt,
                job.reviewedBy,
                job.reviewedAt,
                job.importedBy,
                job.importedAt,
                job.importErrorMessage,
                job.importedLyricId
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

    private String normalizeOperator(String operator) {
        if (operator == null || operator.isBlank()) {
            return "admin";
        }
        return operator.trim();
    }

    private String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
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

    private String sha256File(Path path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return HexFormat.of().formatHex(digest);
        } catch (IOException exception) {
            throw new BadRequestException("Alignment result is not readable");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String safeMessage(Throwable exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private String stripExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int index = fileName.lastIndexOf('.');
        return index <= 0 ? fileName : fileName.substring(0, index);
    }

    private record AudioMapping(String relativePath, String workerPath) {
    }

    private record ImportSource(Path lrcPath, Path swlrcPath) {
    }

    private record ImportedFiles(Path lrcPath, Path swlrcPath, String lrcContent) {
    }

    public enum AlignmentArtifact {
        REPORT("report.json", LyricAlignmentResultReader.RESULT_DIR + "/" + LyricAlignmentResultReader.REPORT_JSON, APPLICATION_JSON_UTF8),
        LRC("lyrics.lrc", LyricAlignmentResultReader.RESULT_DIR + "/" + LyricAlignmentResultReader.LYRICS_LRC, TEXT_PLAIN_UTF8),
        SWLRC("lyrics.swlrc", LyricAlignmentResultReader.RESULT_DIR + "/" + LyricAlignmentResultReader.LYRICS_SWLRC, TEXT_PLAIN_UTF8),
        ALIGNMENT("alignment.json", LyricAlignmentResultReader.RESULT_DIR + "/" + LyricAlignmentResultReader.ALIGNMENT_JSON, APPLICATION_JSON_UTF8);

        private final String fileName;
        private final String relativePath;
        private final String mediaType;

        AlignmentArtifact(String fileName, String relativePath, String mediaType) {
            this.fileName = fileName;
            this.relativePath = relativePath;
            this.mediaType = mediaType;
        }

        public String fileName() {
            return fileName;
        }

        public String relativePath() {
            return relativePath;
        }

        public String mediaType() {
            return mediaType;
        }
    }
}
