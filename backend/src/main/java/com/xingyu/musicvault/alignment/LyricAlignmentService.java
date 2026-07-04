package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobListItemResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ArtifactContent;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ConfirmLyricDraftRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ConfirmLyricDraftResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.CreateAlignmentJobRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.CreateLyricDraftJobRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ImportAlignmentJobRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ImportAlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.LyricDraftResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.LyricDraftDefaultOptionsResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.LyricDraftTrustedAssetResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.MusicLyricDraftContextResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.RejectLyricDraftRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ReviewAlignmentJobRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.UpdateLyricDraftRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.WorkerSignalsResponse;
import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.LyricRepository;
import com.xingyu.musicvault.lyrics.ManagedLyricAssetPathService;
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
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_ABANDONED = "ABANDONED";
    private static final String REVIEW_NOT_AVAILABLE = "NOT_AVAILABLE";
    private static final String REVIEW_PENDING = "PENDING";
    private static final String REVIEW_APPROVED = "APPROVED";
    private static final String REVIEW_REJECTED = "REJECTED";
    private static final String IMPORT_NOT_IMPORTED = "NOT_IMPORTED";
    private static final String IMPORT_IMPORTED = "IMPORTED";
    private static final String IMPORT_FAILED = "IMPORT_FAILED";
    private static final String TASK_ALIGNMENT = "LYRICS_ALIGNMENT";
    private static final String TASK_DRAFT = "LYRIC_DRAFT_EXTRACTION";
    private static final String SOURCE_ALIGNMENT = "ALIGNMENT";
    private static final String SOURCE_DRAFT_CONFIRMED = "DRAFT_CONFIRMED";
    private static final String MATCH_TITLE_ARTIST = "TITLE_ARTIST";
    private static final String DRAFT_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String DRAFT_EDITING = "EDITING";
    private static final String DRAFT_CONFIRMED = "CONFIRMED";
    private static final String DRAFT_REJECTED = "REJECTED";
    private static final int MAX_DRAFT_LINES = 2000;
    private static final String TEXT_PLAIN_UTF8 = "text/plain; charset=UTF-8";
    private static final String APPLICATION_JSON_UTF8 = "application/json; charset=UTF-8";

    @Inject
    MusicVaultConfig config;

    @Inject
    LyricRepository lyricRepository;

    @Inject
    ManagedLyricAssetPathService managedLyricAssetPathService;

    @Inject
    SongLyricRepository songLyricRepository;

    @Inject
    EntityManager entityManager;

    @Inject
    LyricAlignmentJobRepository jobRepository;

    @Inject
    LyricDraftRepository draftRepository;

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
        LyricSource lyricSource = resolveAlignmentLyricSource(request.songId(), request.sourceLyricsAssetId());
        Lyric lyric = lyricSource.lyric();
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
        ObjectNode requestSnapshot = requestSnapshot(jobId, trackFile, lyric.id, audioMapping, trustedLyricsHash, createdBy, request);
        String requestSnapshotJson = writeJson(requestSnapshot);

        LyricAlignmentJob job = QuarkusTransaction.requiringNew().call(() -> {
            LyricAlignmentJob value = new LyricAlignmentJob();
            value.id = jobId;
            value.taskType = TASK_ALIGNMENT;
            value.songId = request.songId();
            value.lyricId = lyric.id;
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

    public AlignmentJobResponse createDraftJob(Long musicId, CreateLyricDraftJobRequest request) {
        if (musicId == null) {
            throw new BadRequestException("musicId is required");
        }
        String createdBy = normalizeCreatedBy(request == null ? null : request.createdBy());
        TrackFile trackFile = TrackFile.findById(musicId);
        if (trackFile == null) {
            throw new NotFoundException("Song not found");
        }
        validateAudioFilePath(trackFile.filePath);
        if (jobRepository.findActiveDraftJobForSong(musicId) != null) {
            throw new ConflictException("A lyric draft extraction job is already queued or running for this song");
        }

        Path jobsRoot = resolveWritableJobsRoot();
        AudioMapping audioMapping = mapAudioPath(trackFile.filePath);
        String jobId = UUID.randomUUID().toString();
        Path jobDir = jobsRoot.resolve(jobId).normalize();
        if (!jobDir.startsWith(jobsRoot)) {
            throw new BadRequestException("Invalid lyric draft job id");
        }
        ObjectNode requestSnapshot = draftRequestSnapshot(jobId, audioMapping, request);
        String requestSnapshotJson = writeJson(requestSnapshot);

        QuarkusTransaction.requiringNew().run(() -> {
            LyricAlignmentJob value = new LyricAlignmentJob();
            value.id = jobId;
            value.taskType = TASK_DRAFT;
            value.songId = musicId;
            value.lyricId = null;
            value.status = STATUS_CREATING;
            value.reviewStatus = REVIEW_NOT_AVAILABLE;
            value.importStatus = IMPORT_NOT_IMPORTED;
            value.audioRelativePath = audioMapping.relativePath();
            value.workerAudioPath = audioMapping.workerPath();
            value.trustedLyricsHash = null;
            value.trustedLyricsSnapshot = null;
            value.requestSnapshotJson = requestSnapshotJson;
            value.jobDir = jobDir.toString();
            value.createdBy = createdBy;
            value.persist();
        });

        try {
            jobWriter.writeRequestOnly(jobDir, requestSnapshot);
            QuarkusTransaction.requiringNew().run(() -> {
                LyricAlignmentJob queuedJob = jobRepository.findById(jobId);
                queuedJob.status = STATUS_QUEUED;
                queuedJob.queuedAt = LocalDateTime.now();
            });
            jobWriter.markReady(jobDir);
        } catch (IOException | RuntimeException exception) {
            LOG.errorf(exception, "Failed to create lyric draft job: jobId=%s songId=%d", jobId, musicId);
            cleanupPartialJobDirectory(jobsRoot, jobDir);
            markFailed(jobId, exception.getMessage());
        }

        return get(jobId);
    }

    public MusicLyricDraftContextResponse getMusicDraftContext(Long musicId) {
        if (musicId == null) {
            throw new BadRequestException("musicId is required");
        }
        if (TrackFile.findById(musicId) == null) {
            throw new NotFoundException("Song not found");
        }
        LyricAlignmentJob latestJob = jobRepository.findLatestDraftJobForSong(musicId);
        LyricDraft draft = latestJob == null ? null : draftRepository.findByJobId(latestJob.id);
        Lyric trusted = draft == null || draft.confirmedTrustedLyricsId == null
                ? null
                : lyricRepository.findById(draft.confirmedTrustedLyricsId);
        return new MusicLyricDraftContextResponse(
                musicId,
                draftDefaultOptions(),
                latestJob == null ? null : toResponse(latestJob),
                draft == null || latestJob == null ? null : toDraftResponse(latestJob, draft),
                trusted == null ? null : trustedAssetResponse(trusted)
        );
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

    public ArtifactContent getDraftArtifact(String id, DraftArtifact artifact) {
        LyricAlignmentJob job = requireDraftJob(id);
        Path jobsRoot = Path.of(config.alignmentJobsDir()).toAbsolutePath().normalize();
        Path jobDir = Path.of(job.jobDir).toAbsolutePath().normalize();
        if (!jobDir.startsWith(jobsRoot) || jobDir.equals(jobsRoot)) {
            throw new BadRequestException("Lyric draft job directory is invalid");
        }
        Path artifactPath = jobDir.resolve(artifact.relativePath()).normalize();
        if (!artifactPath.startsWith(jobDir)) {
            throw new BadRequestException("Lyric draft artifact path is invalid");
        }
        if (!Files.isRegularFile(artifactPath)) {
            throw new NotFoundException("Lyric draft artifact not found");
        }
        try {
            int maxBytes = Math.max(1024, config.alignmentDraftMaxTextBytes());
            if (Files.size(artifactPath) > maxBytes) {
                throw new BadRequestException("Lyric draft artifact exceeds maximum allowed size");
            }
            return new ArtifactContent(artifact.fileName(), artifact.mediaType(), Files.readAllBytes(artifactPath));
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to read lyric draft artifact: jobId=%s artifact=%s", id, artifact.fileName());
            throw new BadRequestException("Lyric draft artifact is not readable");
        }
    }

    public LyricDraftResponse getDraft(String jobId) {
        LyricAlignmentJob job = requireDraftJob(jobId);
        LyricDraft draft = draftRepository.findByJobId(jobId);
        if (draft == null) {
            throw new NotFoundException("Lyric draft not found");
        }
        return toDraftResponse(job, draft);
    }

    public LyricDraftResponse updateDraft(String jobId, UpdateLyricDraftRequest request) {
        String operator = normalizeOperator(request == null ? null : request.editedBy());
        String text = normalizeDraftText(request == null ? null : request.text());
        return QuarkusTransaction.requiringNew().call(() -> {
            LyricAlignmentJob job = requireDraftJob(jobId);
            LyricDraft draft = draftRepository.findByJobId(jobId);
            if (draft == null) {
                throw new NotFoundException("Lyric draft not found");
            }
            validateDraftEditable(job, draft);
            draft.editableText = text;
            draft.editableTextHash = sha256(text);
            draft.draftStatus = DRAFT_EDITING;
            draft.editedBy = operator;
            draft.editedAt = LocalDateTime.now();
            return toDraftResponse(job, draft);
        });
    }

    public ConfirmLyricDraftResponse confirmDraft(String jobId, ConfirmLyricDraftRequest request) {
        String operator = normalizeOperator(request == null ? null : request.confirmedBy());
        String note = normalizeNote(request == null ? null : request.note());
        try {
            return QuarkusTransaction.requiringNew().call(() -> {
                LyricAlignmentJob job = requireDraftJob(jobId);
                LyricDraft draft = draftRepository.findByJobId(jobId);
                if (draft == null) {
                    throw new NotFoundException("Lyric draft not found");
                }
                if (DRAFT_CONFIRMED.equals(draft.draftStatus) && draft.confirmedTrustedLyricsId != null) {
                    Lyric existing = lyricRepository.findById(draft.confirmedTrustedLyricsId);
                    if (existing == null) {
                        throw new ConflictException("Confirmed trusted lyric record is missing");
                    }
                    return confirmDraftResponse(draft, existing.id);
                }
                validateDraftEditable(job, draft);
                TrustedDraftAsset asset = writeTrustedDraftAsset(job, draft);

                TrackFile trackFile = TrackFile.findById(job.songId);
                if (trackFile == null) {
                    throw new NotFoundException("Song not found");
                }
                Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
                LocalDateTime now = LocalDateTime.now();
                Lyric lyric = lyricRepository.findDraftConfirmedBySourceTaskId(job.id);
                if (lyric == null) {
                    lyric = new Lyric();
                }
                lyric.title = track == null ? stripExtension(trackFile.fileName) : track.title;
                lyric.artist = track == null ? null : track.artist;
                lyric.album = track == null ? null : track.album;
                lyric.language = null;
                lyric.sourceType = SOURCE_DRAFT_CONFIRMED;
                lyric.sourcePath = asset.path().toString();
                lyric.content = draft.editableText;
                lyric.contentHash = draft.editableTextHash;
                lyric.format = "TEXT";
                lyric.parseStatus = "PARSED";
                lyric.parseMessage = note;
                lyric.sourceTaskId = job.id;
                lyric.sourceDraftId = draft.id;
                lyric.sourceTextHash = draft.editableTextHash;
                lyric.parentLyricsId = null;
                lyric.confirmedAt = now;
                lyric.confirmedBy = operator;
                if (lyric.id == null) {
                    lyric.persist();
                }

                draft.draftStatus = DRAFT_CONFIRMED;
                draft.confirmedTrustedLyricsId = lyric.id;
                draft.confirmedBy = operator;
                if (draft.confirmedAt == null) {
                    draft.confirmedAt = now;
                }
                draft.errorMessage = null;
                recordEvent(job, "DRAFT_CONFIRMED", operator, note, null, DRAFT_CONFIRMED, null);
                return confirmDraftResponse(draft, lyric.id);
            });
        } catch (RuntimeException exception) {
            recordDraftError(jobId, exception.getMessage());
            throw exception;
        }
    }

    public LyricDraftResponse rejectDraft(String jobId, RejectLyricDraftRequest request) {
        String operator = normalizeOperator(request == null ? null : request.rejectedBy());
        String note = normalizeRejectNote(request == null ? null : request.rejectNote());
        return QuarkusTransaction.requiringNew().call(() -> {
            LyricAlignmentJob job = requireDraftJob(jobId);
            LyricDraft draft = draftRepository.findByJobId(jobId);
            if (draft == null) {
                throw new NotFoundException("Lyric draft not found");
            }
            if (DRAFT_CONFIRMED.equals(draft.draftStatus)) {
                throw new ConflictException("Confirmed lyric drafts cannot be rejected");
            }
            if (DRAFT_REJECTED.equals(draft.draftStatus)) {
                throw new ConflictException("Lyric draft has already been rejected");
            }
            draft.draftStatus = DRAFT_REJECTED;
            draft.rejectedBy = operator;
            draft.rejectedAt = LocalDateTime.now();
            draft.rejectNote = note;
            recordEvent(job, "DRAFT_REJECTED", operator, note, null, DRAFT_REJECTED, null);
            return toDraftResponse(job, draft);
        });
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
            importedFiles = copyResultFilesToManagedLyrics(job, importSource);
        } catch (ConflictException exception) {
            LOG.warnf(exception, "Failed to publish alignment result assets because target conflicts: jobId=%s", id);
            markImportFailed(id, operator, exception.getMessage());
            throw exception;
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
                assertImportedLrcContentHash(lyric.content, importedFiles.lrcRawHash(), importJob.lrcHash);
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
                if (!TASK_ALIGNMENT.equals(taskType(job))) {
                    throw new ConflictException("Lyric draft jobs cannot be reviewed as alignment jobs");
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

    private LyricAlignmentJob requireDraftJob(String jobId) {
        LyricAlignmentJob job = jobRepository.findById(jobId);
        if (job == null) {
            throw new NotFoundException("Lyric draft job not found");
        }
        if (!TASK_DRAFT.equals(job.taskType)) {
            throw new BadRequestException("Job is not a lyric draft extraction job");
        }
        return job;
    }

    private void validateDraftEditable(LyricAlignmentJob job, LyricDraft draft) {
        if (!STATUS_COMPLETED.equals(job.status)) {
            throw new ConflictException("Only completed lyric draft jobs can be edited or confirmed");
        }
        if (!DRAFT_PENDING_REVIEW.equals(draft.draftStatus) && !DRAFT_EDITING.equals(draft.draftStatus)) {
            throw new ConflictException("Lyric draft cannot be edited or confirmed in current status");
        }
    }

    private String normalizeDraftText(String text) {
        if (text == null) {
            throw new BadRequestException("text is required");
        }
        String normalized = stripBom(text).replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char ch = normalized.charAt(index);
            if ((Character.isISOControl(ch) && ch != '\n' && ch != '\t') || ch == '\u007F') {
                continue;
            }
            builder.append(ch);
        }
        normalized = builder.toString();
        if (normalized.isBlank()) {
            throw new BadRequestException("Lyric draft text cannot be empty");
        }
        int maxBytes = Math.max(1024, config.alignmentDraftMaxTextBytes());
        if (normalized.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new BadRequestException("Lyric draft text exceeds maximum allowed size");
        }
        long lineCount = normalized.lines().count();
        if (lineCount > MAX_DRAFT_LINES) {
            throw new BadRequestException("Lyric draft text has too many lines");
        }
        return normalized.endsWith("\n") ? normalized : normalized + "\n";
    }

    private String normalizeRejectNote(String note) {
        if (note == null || note.isBlank()) {
            throw new BadRequestException("rejectNote is required");
        }
        return note.trim();
    }

    private TrustedDraftAsset writeTrustedDraftAsset(LyricAlignmentJob job, LyricDraft draft) {
        try {
            Path root = resolveWritableAlignmentAssetsRoot();
            Path targetDir = root.resolve(String.valueOf(job.songId)).resolve("drafts").normalize();
            if (!targetDir.startsWith(root) || targetDir.equals(root)) {
                throw new BadRequestException("Lyric draft asset directory is invalid");
            }
            Files.createDirectories(targetDir);
            if (!Files.isWritable(targetDir)) {
                throw new BadRequestException("Lyric draft asset directory is not writable");
            }
            Path target = targetDir.resolve(job.id + ".trusted-lyrics.txt").normalize();
            if (!target.startsWith(targetDir)) {
                throw new BadRequestException("Lyric draft asset path is invalid");
            }
            if (Files.exists(target) && draft.editableTextHash.equals(sha256File(target))) {
                return new TrustedDraftAsset(target);
            }
            if (Files.exists(target)) {
                throw new BadRequestException("Lyric draft trusted asset already exists with different content");
            }
            Path temp = target.resolveSibling("." + target.getFileName() + ".tmp-" + UUID.randomUUID());
            boolean moved = false;
            try {
                Files.writeString(temp, draft.editableText, StandardCharsets.UTF_8);
                if (!draft.editableTextHash.equals(sha256File(temp))) {
                    throw new BadRequestException("Lyric draft trusted asset hash does not match source text");
                }
                try {
                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException exception) {
                    LOG.warnf(exception, "Atomic move is not supported for lyric draft trusted asset; falling back to replace move: target=%s", target);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                moved = true;
            } finally {
                if (!moved) {
                    Files.deleteIfExists(temp);
                }
            }
            return new TrustedDraftAsset(target);
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to write lyric draft trusted asset: jobId=%s", job.id);
            throw new BadRequestException("Failed to write lyric draft trusted asset: " + safeMessage(exception));
        }
    }

    private void recordDraftError(String jobId, String message) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                LyricDraft draft = draftRepository.findByJobId(jobId);
                if (draft != null && !DRAFT_CONFIRMED.equals(draft.draftStatus)) {
                    draft.errorMessage = message == null || message.isBlank()
                            ? "Failed to confirm lyric draft"
                            : message;
                }
            });
        } catch (RuntimeException exception) {
            LOG.errorf(exception, "Failed to record lyric draft error: jobId=%s", jobId);
        }
    }

    private void validateImportableStatus(LyricAlignmentJob job) {
        if (job == null) {
            throw new NotFoundException("Alignment job not found");
        }
        if (!TASK_ALIGNMENT.equals(taskType(job))) {
            throw new ConflictException("Only alignment jobs can be imported");
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

    private ImportedFiles copyResultFilesToManagedLyrics(LyricAlignmentJob job, ImportSource source) throws IOException {
        ManagedLyricAssetPathService.AlignmentAssetPaths target = managedLyricAssetPathService.alignmentAssetPaths(job.songId, job.id);
        Path finalDir = target.finalDir();
        Path parentDir = finalDir.getParent();
        if (parentDir == null || !parentDir.startsWith(target.managedRoot())) {
            throw new BadRequestException("Alignment lyric asset directory is invalid");
        }
        Files.createDirectories(parentDir);
        if (!Files.isWritable(parentDir)) {
            throw new BadRequestException("Alignment lyric asset directory is not writable");
        }

        Path stagingDir = finalDir.resolveSibling("." + finalDir.getFileName() + ".staging-" + UUID.randomUUID()).normalize();
        if (!stagingDir.startsWith(parentDir) || stagingDir.equals(parentDir)) {
            throw new BadRequestException("Alignment lyric staging directory is invalid");
        }

        boolean published = false;
        try {
            Files.createDirectories(stagingDir);
            Path stagingLrc = stagingDir.resolve(LyricAlignmentResultReader.LYRICS_LRC).normalize();
            Path stagingSwlrc = stagingDir.resolve(LyricAlignmentResultReader.LYRICS_SWLRC).normalize();
            copyAndVerifyStagingAsset(source.lrcPath(), stagingLrc);
            copyAndVerifyStagingAsset(source.swlrcPath(), stagingSwlrc);
            verifyHash("Staged alignment LRC asset", job.lrcHash, stagingLrc);
            verifyHash("Staged alignment SWLRC asset", job.swlrcHash, stagingSwlrc);

            if (Files.exists(finalDir)) {
                ImportedFiles existing = existingImportedFiles(finalDir, job);
                cleanupStagingDirectory(stagingDir, parentDir);
                return existing;
            }

            try {
                Files.move(stagingDir, finalDir, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                LOG.warnf(exception, "Atomic directory move is not supported for imported alignment assets; falling back to regular move: target=%s", finalDir);
                Files.move(stagingDir, finalDir);
            }
            published = true;
            String publishedLrcHash = verifyHash("Published alignment LRC asset", job.lrcHash, target.lrcPath());
            verifyHash("Published alignment SWLRC asset", job.swlrcHash, target.swlrcPath());
            String lrcContent = Files.readString(target.lrcPath(), StandardCharsets.UTF_8);
            return new ImportedFiles(target.lrcPath(), target.swlrcPath(), lrcContent, publishedLrcHash);
        } finally {
            if (!published) {
                cleanupStagingDirectory(stagingDir, parentDir);
            }
        }
    }

    private void copyAndVerifyStagingAsset(Path source, Path target) throws IOException {
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        String sourceHash = sha256File(source);
        String targetHash = sha256File(target);
        if (!sourceHash.equals(targetHash)) {
            throw new BadRequestException("Copied alignment asset hash does not match source hash");
        }
    }

    private ImportedFiles existingImportedFiles(Path finalDir, LyricAlignmentJob job) throws IOException {
        if (!Files.isDirectory(finalDir)) {
            throw new ConflictException("Alignment lyric asset target already exists and is not a directory");
        }
        Path lrcPath = finalDir.resolve(LyricAlignmentResultReader.LYRICS_LRC).normalize();
        Path swlrcPath = finalDir.resolve(LyricAlignmentResultReader.LYRICS_SWLRC).normalize();
        if (!Files.isRegularFile(lrcPath) || !Files.isRegularFile(swlrcPath)) {
            throw new ConflictException("Alignment lyric asset target already exists but is incomplete");
        }
        String lrcHash = sha256File(lrcPath);
        if (!job.lrcHash.equals(lrcHash) || !job.swlrcHash.equals(sha256File(swlrcPath))) {
            throw new ConflictException("Alignment lyric asset target already exists with different content");
        }
        String lrcContent = Files.readString(lrcPath, StandardCharsets.UTF_8);
        return new ImportedFiles(lrcPath, swlrcPath, lrcContent, lrcHash);
    }

    private String verifyHash(String label, String expectedHash, Path path) {
        String actualHash = sha256File(path);
        if (!expectedHash.equals(actualHash)) {
            throw new BadRequestException(label + " hash does not match expected hash");
        }
        return actualHash;
    }

    private void assertImportedLrcContentHash(String content, String rawHash, String jobHash) {
        if (!jobHash.equals(rawHash)) {
            throw new BadRequestException("Imported alignment LRC raw hash does not match job hash");
        }
        String contentHash = sha256(content);
        if (!jobHash.equals(contentHash)) {
            throw new BadRequestException("Imported alignment LRC content hash does not match job hash");
        }
    }

    private void cleanupStagingDirectory(Path stagingDir, Path parentDir) {
        if (stagingDir == null || parentDir == null || !stagingDir.startsWith(parentDir) || stagingDir.equals(parentDir) || !Files.exists(stagingDir)) {
            return;
        }
        try (var paths = Files.walk(stagingDir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to clean alignment lyric staging directory: stagingDir=%s", stagingDir);
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
            existing.matchType = MATCH_TITLE_ARTIST;
            existing.matchScore = 100;
            return;
        }
        SongLyric binding = new SongLyric();
        binding.songId = songId;
        binding.lyricId = lyricId;
        binding.matchType = MATCH_TITLE_ARTIST;
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
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to prepare alignment job directory: root=%s", root);
            throw new BadRequestException("Alignment job directory is not writable");
        }
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

    private LyricSource resolveAlignmentLyricSource(Long songId, Long sourceLyricsAssetId) {
        if (sourceLyricsAssetId == null) {
            SongLyric binding = songLyricRepository.findPrimaryBySongId(songId);
            if (binding == null) {
                throw new BadRequestException("Trusted lyric not found");
            }
            Lyric lyric = lyricRepository.findById(binding.lyricId);
            return new LyricSource(lyric, true);
        }
        Lyric lyric = lyricRepository.findById(sourceLyricsAssetId);
        if (lyric == null) {
            throw new NotFoundException("Trusted lyric asset not found");
        }
        SongLyric binding = songLyricRepository.findBySongIdAndLyricId(songId, sourceLyricsAssetId);
        if (binding != null) {
            return new LyricSource(lyric, true);
        }
        if (SOURCE_DRAFT_CONFIRMED.equals(lyric.sourceType) && lyric.sourceTaskId != null) {
            LyricAlignmentJob sourceJob = jobRepository.findById(lyric.sourceTaskId);
            if (sourceJob != null && songId.equals(sourceJob.songId)) {
                return new LyricSource(lyric, false);
            }
        }
        throw new BadRequestException("Trusted lyric asset does not belong to this song");
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

    private ObjectNode draftRequestSnapshot(String jobId, AudioMapping audioMapping, CreateLyricDraftJobRequest request) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("schemaVersion", 2);
        node.put("jobId", jobId);
        node.put("taskType", TASK_DRAFT);
        node.put("audioPath", audioMapping.workerPath());
        node.put("outputDir", workerJobPath(jobId, "result"));
        node.put("language", nonBlankOrDefault(request == null ? null : request.language(), "zh"));
        node.put("device", "cpu");
        node.put("asrModel", nonBlankOrDefault(
                request == null ? null : request.asrModel(),
                nonBlankOrDefault(config.alignmentDraftDefaultAsrModel(), "medium")
        ));
        node.put("skipSeparation", request == null || request.skipSeparation() == null
                ? config.alignmentDraftDefaultSkipSeparation()
                : Boolean.TRUE.equals(request.skipSeparation()));
        node.put("vadFilter", request == null || request.vadFilter() == null
                ? config.alignmentDraftDefaultVadFilter()
                : Boolean.TRUE.equals(request.vadFilter()));
        node.put("conditionOnPreviousText", request != null && Boolean.TRUE.equals(request.conditionOnPreviousText()));
        node.put("keepSuspectedMetadata", request != null && Boolean.TRUE.equals(request.keepSuspectedMetadata()));
        node.put("retainIntermediate", request != null && Boolean.TRUE.equals(request.retainIntermediate()));
        node.put("createdAt", LocalDateTime.now().toString());
        return node;
    }

    private String nonBlankOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
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
                taskType(job),
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
                workerSignals(job),
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
                job.importedLyricId,
                draftStatus(job),
                confirmedTrustedLyricsId(job)
        );
    }

    private AlignmentJobListItemResponse toListItemResponse(LyricAlignmentJob job) {
        return new AlignmentJobListItemResponse(
                job.id,
                taskType(job),
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
                workerSignals(job),
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
                job.importedLyricId,
                draftStatus(job),
                confirmedTrustedLyricsId(job)
        );
    }

    private LyricDraftDefaultOptionsResponse draftDefaultOptions() {
        return new LyricDraftDefaultOptionsResponse(
                "zh",
                nonBlankOrDefault(config.alignmentDraftDefaultAsrModel(), "medium"),
                config.alignmentDraftDefaultSkipSeparation(),
                config.alignmentDraftDefaultVadFilter(),
                false,
                false,
                false
        );
    }

    private WorkerSignalsResponse workerSignals(LyricAlignmentJob job) {
        if (job == null || job.jobDir == null || job.jobDir.isBlank()) {
            return new WorkerSignalsResponse(false, false, false, false, false, false, false, false, false, false, "任务目录尚未创建");
        }
        Path jobDir = Path.of(job.jobDir).toAbsolutePath().normalize();
        boolean jobDirectoryAvailable = Files.isDirectory(jobDir);
        boolean ready = jobDirectoryAvailable && Files.exists(jobDir.resolve("READY"));
        boolean running = jobDirectoryAvailable && Files.exists(jobDir.resolve("RUNNING"));
        boolean succeeded = jobDirectoryAvailable && Files.exists(jobDir.resolve("SUCCEEDED"));
        boolean needsReview = jobDirectoryAvailable && Files.exists(jobDir.resolve("NEEDS_REVIEW"));
        boolean failed = jobDirectoryAvailable && Files.exists(jobDir.resolve("FAILED"));
        boolean abandoned = jobDirectoryAvailable && Files.exists(jobDir.resolve("ABANDONED"));
        boolean statusJsonAvailable = jobDirectoryAvailable && Files.isRegularFile(jobDir.resolve("status.json"));
        boolean resultDirectoryAvailable = jobDirectoryAvailable && Files.isDirectory(jobDir.resolve("result"));
        boolean stderrLogAvailable = jobDirectoryAvailable && Files.isRegularFile(jobDir.resolve("stderr.log"));
        return new WorkerSignalsResponse(
                jobDirectoryAvailable,
                ready,
                running,
                succeeded,
                needsReview,
                failed,
                abandoned,
                statusJsonAvailable,
                resultDirectoryAvailable,
                stderrLogAvailable,
                workerStageMessage(job, jobDirectoryAvailable, ready, running, succeeded, needsReview, failed, abandoned, statusJsonAvailable, resultDirectoryAvailable)
        );
    }

    private String workerStageMessage(
            LyricAlignmentJob job,
            boolean jobDirectoryAvailable,
            boolean ready,
            boolean running,
            boolean succeeded,
            boolean needsReview,
            boolean failed,
            boolean abandoned,
            boolean statusJsonAvailable,
            boolean resultDirectoryAvailable
    ) {
        if (!jobDirectoryAvailable) {
            return "任务目录不可用，音库无法继续同步 Worker 状态";
        }
        if (failed || STATUS_FAILED.equals(job.status)) {
            return "Worker 已标记失败或音库已记录失败";
        }
        if (abandoned || STATUS_ABANDONED.equals(job.status)) {
            return "Worker 已放弃任务";
        }
        if (succeeded || needsReview || STATUS_COMPLETED.equals(job.status)) {
            return resultDirectoryAvailable ? "Worker 已产生结果，音库可读取结果" : "Worker 已写入终态标记，结果目录暂未就绪";
        }
        if (running || STATUS_RUNNING.equals(job.status)) {
            return statusJsonAvailable ? "Worker 正在执行并写入状态" : "Worker 已领取任务，等待 status.json";
        }
        if (ready || STATUS_QUEUED.equals(job.status)) {
            return "READY 已写入，正在等待 Worker 领取任务";
        }
        return "音库正在准备任务输入文件";
    }

    private LyricDraftTrustedAssetResponse trustedAssetResponse(Lyric lyric) {
        return new LyricDraftTrustedAssetResponse(
                lyric.id,
                lyric.sourceType,
                lyric.contentHash,
                lyric.confirmedAt,
                lyric.confirmedBy
        );
    }

    private String draftStatus(LyricAlignmentJob job) {
        if (!TASK_DRAFT.equals(taskType(job))) {
            return null;
        }
        LyricDraft draft = draftRepository.findByJobId(job.id);
        return draft == null ? null : draft.draftStatus;
    }

    private Long confirmedTrustedLyricsId(LyricAlignmentJob job) {
        if (!TASK_DRAFT.equals(taskType(job))) {
            return null;
        }
        LyricDraft draft = draftRepository.findByJobId(job.id);
        return draft == null ? null : draft.confirmedTrustedLyricsId;
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

    private LyricDraftResponse toDraftResponse(LyricAlignmentJob job, LyricDraft draft) {
        return new LyricDraftResponse(
                draft.jobId,
                draft.musicId,
                job.status,
                draft.draftStatus,
                draft.originalText,
                draft.originalTextHash,
                draft.editableText,
                draft.editableTextHash,
                readJson(draft.reportSummaryJson),
                draft.createdAt,
                draft.updatedAt,
                draft.editedBy,
                draft.editedAt,
                draft.confirmedBy,
                draft.confirmedAt,
                draft.confirmedTrustedLyricsId,
                draft.rejectedBy,
                draft.rejectedAt,
                draft.rejectNote,
                draft.errorMessage
        );
    }

    private ConfirmLyricDraftResponse confirmDraftResponse(LyricDraft draft, Long trustedLyricId) {
        return new ConfirmLyricDraftResponse(
                draft.jobId,
                draft.id,
                trustedLyricId,
                draft.draftStatus,
                draft.editableTextHash,
                draft.confirmedAt,
                draft.confirmedBy
        );
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

    private String taskType(LyricAlignmentJob job) {
        return job.taskType == null || job.taskType.isBlank() ? TASK_ALIGNMENT : job.taskType;
    }

    private String stripBom(String value) {
        if (value != null && !value.isEmpty() && value.charAt(0) == '\uFEFF') {
            return value.substring(1);
        }
        return value;
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

    private record LyricSource(Lyric lyric, boolean boundToSong) {
    }

    private record ImportSource(Path lrcPath, Path swlrcPath) {
    }

    private record ImportedFiles(Path lrcPath, Path swlrcPath, String lrcContent, String lrcRawHash) {
    }

    private record TrustedDraftAsset(Path path) {
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

    public enum DraftArtifact {
        CLEANED("transcript.cleaned.txt", LyricDraftResultReader.RESULT_DIR + "/" + LyricDraftResultReader.TRANSCRIPT_CLEANED, TEXT_PLAIN_UTF8),
        RAW("transcript.raw.txt", LyricDraftResultReader.RESULT_DIR + "/" + LyricDraftResultReader.TRANSCRIPT_RAW, TEXT_PLAIN_UTF8),
        SEGMENTS("transcript.segments.json", LyricDraftResultReader.RESULT_DIR + "/" + LyricDraftResultReader.TRANSCRIPT_SEGMENTS, APPLICATION_JSON_UTF8),
        REPORT("report.json", LyricDraftResultReader.RESULT_DIR + "/" + LyricDraftResultReader.REPORT_JSON, APPLICATION_JSON_UTF8);

        private final String fileName;
        private final String relativePath;
        private final String mediaType;

        DraftArtifact(String fileName, String relativePath, String mediaType) {
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
