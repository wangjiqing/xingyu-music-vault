package com.xingyu.musicvault.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataRollbackPreviewResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataRollbackRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataRollbackResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditDetailResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditListItem;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditPageResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditCreateRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataRollbackPreviewResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataRollbackRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataRollbackResult;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSnapshot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@ApplicationScoped
public class MetadataAuditService {
    private static final Logger LOG = Logger.getLogger(MetadataAuditService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 20;
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String ROLLBACK_NOT_ROLLED_BACK = "NOT_ROLLED_BACK";
    private static final String ROLLBACK_ROLLED_BACK = "ROLLED_BACK";
    private static final String OPERATION_ROLLBACK = "ROLLBACK";
    private static final String DIRECTION_FILE_TO_DB = "file_to_db";
    private static final String DIRECTION_DB_TO_FILE = "db_to_file";
    private static final String TARGET_DATABASE = "database";
    private static final String TARGET_EMBEDDED_TAG = "embedded_tag";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AudioMetadataService audioMetadataService;

    public MusicMetadataSyncAudit create(MetadataAuditCreateRequest request) {
        MusicMetadataSyncAudit audit = new MusicMetadataSyncAudit();
        audit.batchId = request.batchId();
        audit.musicId = request.musicId();
        audit.filePath = request.filePath();
        audit.direction = request.direction();
        audit.sourceType = request.sourceType();
        audit.targetType = request.targetType();
        audit.mode = request.mode();
        audit.operationType = request.operationType();
        audit.beforeDatabaseJson = toJson(comparableSnapshot(request.beforeDatabase()));
        audit.afterDatabaseJson = toJson(comparableSnapshot(request.afterDatabase()));
        audit.beforeFileJson = toJson(comparableSnapshot(request.beforeFile()));
        audit.afterFileJson = toJson(comparableSnapshot(request.afterFile()));
        audit.changedFieldsJson = toJson(comparableFields(request.changedFields()));
        audit.status = request.status();
        audit.errorMessage = request.errorMessage();
        audit.rollbackOfAuditId = request.rollbackOfAuditId();
        audit.createdAt = request.createdAt();
        audit.createdBy = request.createdBy();
        audit.persistAndFlush();
        return audit;
    }

    public MetadataAuditPageResponse list(
            Long musicId,
            String batchId,
            String direction,
            String status,
            String rollbackStatus,
            String keyword,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer page,
            Integer pageSize
    ) {
        int normalizedPage = page == null || page < 0 ? 0 : page;
        int normalizedPageSize = normalizePageSize(pageSize);
        List<MetadataAuditListItem> all = MusicMetadataSyncAudit.<MusicMetadataSyncAudit>listAll().stream()
                .sorted(Comparator.comparing((MusicMetadataSyncAudit audit) -> audit.createdAt).reversed())
                .filter(audit -> musicId == null || Objects.equals(audit.musicId, musicId))
                .filter(audit -> isBlank(batchId) || Objects.equals(audit.batchId, batchId))
                .filter(audit -> isBlank(direction) || equalsIgnoreCase(audit.direction, direction))
                .filter(audit -> isBlank(status) || equalsIgnoreCase(audit.status, status))
                .filter(audit -> isBlank(rollbackStatus) || equalsIgnoreCase(audit.rollbackStatus, rollbackStatus))
                .filter(audit -> startTime == null || !audit.createdAt.isBefore(startTime))
                .filter(audit -> endTime == null || !audit.createdAt.isAfter(endTime))
                .map(this::toListItem)
                .filter(item -> keywordMatches(item, keyword))
                .toList();
        int from = Math.min(normalizedPage * normalizedPageSize, all.size());
        int to = Math.min(from + normalizedPageSize, all.size());
        return new MetadataAuditPageResponse(all.subList(from, to), all.size(), normalizedPage, normalizedPageSize);
    }

    public MetadataAuditDetailResponse detail(Long auditId) {
        return toDetail(findAudit(auditId));
    }

    public MetadataRollbackPreviewResponse rollbackPreview(Long auditId) {
        MusicMetadataSyncAudit audit = findAudit(auditId);
        String eligibilityError = rollbackEligibilityError(audit);
        if (eligibilityError != null) {
            return preview(audit, null, null, List.of(), false, List.of(), eligibilityError);
        }
        try {
            TrackFile trackFile = findMusic(audit.musicId);
            String rollbackTarget = rollbackTarget(audit);
            MetadataSnapshot current = TARGET_DATABASE.equals(rollbackTarget)
                    ? databaseSnapshot(trackFile)
                    : audioMetadataService.read(Path.of(trackFile.filePath));
            MetadataSnapshot target = rollbackTargetSnapshot(audit);
            List<MetadataDtos.MetadataDiffItem> diffs = diffs(current, target);
            return preview(audit, current, target, diffs, true, rollbackWarnings(trackFile, rollbackTarget), null);
        } catch (RuntimeException exception) {
            return preview(audit, null, rollbackTargetSnapshot(audit), List.of(), false, List.of(), errorMessage(exception));
        }
    }

    public BatchMetadataRollbackPreviewResponse rollbackPreviewBatch(BatchMetadataRollbackRequest request) {
        List<Long> auditIds = validateAuditIds(request == null ? null : request.auditIds());
        List<MetadataRollbackPreviewResponse> items = auditIds.stream().map(this::rollbackPreview).toList();
        int canRollback = (int) items.stream().filter(MetadataRollbackPreviewResponse::canRollback).count();
        return new BatchMetadataRollbackPreviewResponse(items.size(), canRollback, items.size() - canRollback, items);
    }

    @Transactional
    public MetadataRollbackResult rollback(Long auditId, MetadataRollbackRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new BadRequestException("confirm must be true");
        }
        return rollbackOne(auditId, null, true);
    }

    @Transactional
    public BatchMetadataRollbackResponse rollbackBatch(BatchMetadataRollbackRequest request) {
        if (request == null || !Boolean.TRUE.equals(request.confirm())) {
            throw new BadRequestException("confirm must be true");
        }
        List<Long> auditIds = validateAuditIds(request.auditIds());
        String batchId = UUID.randomUUID().toString();
        List<MetadataRollbackResult> items = auditIds.stream()
                .map(auditId -> rollbackOne(auditId, batchId, false))
                .toList();
        int success = (int) items.stream().filter(MetadataRollbackResult::success).count();
        return new BatchMetadataRollbackResponse(batchId, items.size(), success, items.size() - success, items);
    }

    private MetadataRollbackResult rollbackOne(Long auditId, String batchId, boolean throwOnFailure) {
        MusicMetadataSyncAudit original = null;
        TrackFile trackFile = null;
        String targetType = TARGET_DATABASE;
        MetadataSnapshot beforeDatabase = null;
        MetadataSnapshot afterDatabase = null;
        MetadataSnapshot beforeFile = null;
        MetadataSnapshot afterFile = null;
        List<String> changedFields = List.of();
        try {
            original = findAudit(auditId);
            String eligibilityError = rollbackEligibilityError(original);
            if (eligibilityError != null) {
                throw new BadRequestException(eligibilityError);
            }
            trackFile = findMusic(original.musicId);
            targetType = rollbackTarget(original);
            MetadataSnapshot target = rollbackTargetSnapshot(original);
            beforeDatabase = databaseSnapshot(trackFile);
            if (TARGET_EMBEDDED_TAG.equals(targetType)) {
                beforeFile = audioMetadataService.read(Path.of(trackFile.filePath));
            } else {
                beforeFile = snapshot(original.afterFileJson);
            }
            MetadataSnapshot current = TARGET_DATABASE.equals(targetType) ? beforeDatabase : beforeFile;
            changedFields = diffs(current, target).stream()
                    .map(MetadataDtos.MetadataDiffItem::field)
                    .toList();

            if (TARGET_DATABASE.equals(targetType)) {
                Track track = ensureTrack(trackFile);
                applySnapshotToTrack(track, target);
                track.metadataUpdatedAt = LocalDateTime.now();
                afterDatabase = databaseSnapshot(trackFile, track);
                afterFile = beforeFile;
            } else {
                afterDatabase = beforeDatabase;
                afterFile = audioMetadataService.write(Path.of(trackFile.filePath), target);
            }

            MusicMetadataSyncAudit rollbackAudit = createRollbackAudit(
                    batchId,
                    trackFile,
                    original,
                    targetType,
                    beforeDatabase,
                    afterDatabase,
                    beforeFile,
                    afterFile,
                    changedFields,
                    STATUS_SUCCESS,
                    null
            );
            original.rollbackStatus = ROLLBACK_ROLLED_BACK;
            original.persistAndFlush();
            return new MetadataRollbackResult(auditId, rollbackAudit.id, true, "Rollback completed", null);
        } catch (Exception exception) {
            String message = errorMessage(exception);
            Long rollbackAuditId = null;
            if (original != null && trackFile != null) {
                try {
                    MusicMetadataSyncAudit rollbackAudit = createRollbackAudit(
                            batchId,
                            trackFile,
                            original,
                            targetType,
                            beforeDatabase,
                            afterDatabase,
                            beforeFile,
                            afterFile,
                            changedFields,
                            STATUS_FAILED,
                            message
                    );
                    rollbackAuditId = rollbackAudit.id;
                } catch (RuntimeException auditException) {
                    LOG.errorf(auditException, "Failed to persist rollback failure audit: auditId=%d message=%s", auditId, message);
                    message = message + "; additionally failed to persist rollback failure audit: " + errorMessage(auditException);
                }
            }
            if (throwOnFailure) {
                throw toApiException(exception, message);
            }
            return new MetadataRollbackResult(auditId, rollbackAuditId, false, null, message);
        }
    }

    private MusicMetadataSyncAudit createRollbackAudit(
            String batchId,
            TrackFile trackFile,
            MusicMetadataSyncAudit original,
            String targetType,
            MetadataSnapshot beforeDatabase,
            MetadataSnapshot afterDatabase,
            MetadataSnapshot beforeFile,
            MetadataSnapshot afterFile,
            List<String> changedFields,
            String status,
            String errorMessage
    ) {
        String direction = TARGET_DATABASE.equals(targetType) ? DIRECTION_FILE_TO_DB : DIRECTION_DB_TO_FILE;
        String sourceType = "audit_before_snapshot";
        return create(new MetadataAuditCreateRequest(
                batchId,
                trackFile.id,
                trackFile.filePath,
                direction,
                sourceType,
                targetType,
                "rollback",
                OPERATION_ROLLBACK,
                beforeDatabase,
                afterDatabase,
                beforeFile,
                afterFile,
                changedFields,
                status,
                errorMessage,
                original.id,
                LocalDateTime.now(),
                "api"
        ));
    }

    private MetadataAuditListItem toListItem(MusicMetadataSyncAudit audit) {
        return new MetadataAuditListItem(
                audit.id,
                audit.batchId,
                audit.musicId,
                musicTitle(audit.musicId),
                audit.filePath,
                audit.direction,
                audit.sourceType,
                audit.targetType,
                audit.operationType,
                audit.status,
                audit.rollbackStatus,
                changedFields(audit),
                audit.createdAt,
                audit.errorMessage
        );
    }

    private MetadataAuditDetailResponse toDetail(MusicMetadataSyncAudit audit) {
        return new MetadataAuditDetailResponse(
                audit.id,
                audit.batchId,
                audit.musicId,
                musicTitle(audit.musicId),
                audit.filePath,
                audit.direction,
                audit.sourceType,
                audit.targetType,
                audit.mode,
                audit.operationType,
                audit.status,
                audit.rollbackStatus,
                snapshot(audit.beforeDatabaseJson),
                snapshot(audit.afterDatabaseJson),
                snapshot(audit.beforeFileJson),
                snapshot(audit.afterFileJson),
                changedFields(audit),
                audit.errorMessage,
                audit.rollbackOfAuditId,
                rollbackAuditId(audit.id),
                audit.createdAt,
                audit.createdBy
        );
    }

    private MetadataRollbackPreviewResponse preview(
            MusicMetadataSyncAudit audit,
            MetadataSnapshot current,
            MetadataSnapshot target,
            List<MetadataDtos.MetadataDiffItem> diffs,
            boolean canRollback,
            List<String> warnings,
            String errorMessage
    ) {
        return new MetadataRollbackPreviewResponse(
                audit.id,
                audit.musicId,
                rollbackTarget(audit),
                current,
                target,
                diffs,
                canRollback,
                warnings,
                errorMessage
        );
    }

    private String rollbackEligibilityError(MusicMetadataSyncAudit audit) {
        if (!STATUS_SUCCESS.equals(audit.status)) {
            return "Only SUCCESS audit records can be rolled back";
        }
        if (OPERATION_ROLLBACK.equals(audit.operationType)) {
            return "ROLLBACK audit records cannot be rolled back";
        }
        if (!ROLLBACK_NOT_ROLLED_BACK.equals(audit.rollbackStatus)) {
            return "Audit record has already been rolled back";
        }
        if (!DIRECTION_FILE_TO_DB.equals(audit.direction) && !DIRECTION_DB_TO_FILE.equals(audit.direction)) {
            return "Unsupported rollback direction";
        }
        MetadataSnapshot target = rollbackTargetSnapshot(audit);
        if (target == null) {
            return "Audit record does not contain the required before snapshot";
        }
        return null;
    }

    private String rollbackTarget(MusicMetadataSyncAudit audit) {
        return DIRECTION_FILE_TO_DB.equals(audit.direction) ? TARGET_DATABASE : TARGET_EMBEDDED_TAG;
    }

    private MetadataSnapshot rollbackTargetSnapshot(MusicMetadataSyncAudit audit) {
        return DIRECTION_FILE_TO_DB.equals(audit.direction) ? snapshot(audit.beforeDatabaseJson) : snapshot(audit.beforeFileJson);
    }

    private List<String> rollbackWarnings(TrackFile trackFile, String targetType) {
        if (TARGET_EMBEDDED_TAG.equals(targetType)) {
            return List.of("This rollback will modify the local audio file Tag: " + trackFile.filePath);
        }
        return List.of();
    }

    private Long rollbackAuditId(Long auditId) {
        MusicMetadataSyncAudit rollbackAudit = MusicMetadataSyncAudit.find(
                "rollbackOfAuditId = ?1 and operationType = ?2 order by createdAt desc",
                auditId,
                OPERATION_ROLLBACK
        ).firstResult();
        return rollbackAudit == null ? null : rollbackAudit.id;
    }

    private MetadataSnapshot snapshot(String json) {
        if (isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, MetadataSnapshot.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize metadata audit snapshot", exception);
        }
    }

    private List<String> changedFields(MusicMetadataSyncAudit audit) {
        if (isBlank(audit.changedFieldsJson)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(audit.changedFieldsJson, new TypeReference<>() {
            });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize metadata audit changed fields", exception);
        }
    }

    private MusicMetadataSyncAudit findAudit(Long auditId) {
        if (auditId == null) {
            throw new BadRequestException("auditId is required");
        }
        MusicMetadataSyncAudit audit = MusicMetadataSyncAudit.findById(auditId);
        if (audit == null) {
            throw new NotFoundException("Metadata audit not found");
        }
        return audit;
    }

    private TrackFile findMusic(Long musicId) {
        if (musicId == null) {
            throw new BadRequestException("musicId is required");
        }
        TrackFile trackFile = TrackFile.findById(musicId);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        return trackFile;
    }

    private Track ensureTrack(TrackFile trackFile) {
        Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
        if (track == null) {
            track = new Track();
            track.persist();
            trackFile.trackId = track.id;
        }
        return track;
    }

    private MetadataSnapshot databaseSnapshot(TrackFile trackFile) {
        Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
        return databaseSnapshot(trackFile, track);
    }

    private MetadataSnapshot databaseSnapshot(TrackFile trackFile, Track track) {
        return new MetadataSnapshot(
                track == null ? null : clean(track.title),
                track == null ? null : clean(track.artist),
                track == null ? null : clean(track.album),
                track == null ? null : clean(track.albumArtist),
                track == null ? null : track.year,
                track == null ? null : clean(track.genre),
                track == null ? null : track.trackNo,
                track == null ? null : track.duration
        );
    }

    private List<MetadataDtos.MetadataDiffItem> diffs(MetadataSnapshot current, MetadataSnapshot target) {
        java.util.ArrayList<MetadataDtos.MetadataDiffItem> diffs = new java.util.ArrayList<>();
        addDiff(diffs, "title", current.title(), target.title());
        addDiff(diffs, "artist", current.artist(), target.artist());
        addDiff(diffs, "album", current.album(), target.album());
        return diffs;
    }

    private void addDiff(List<MetadataDtos.MetadataDiffItem> diffs, String field, Object currentValue, Object targetValue) {
        if (!Objects.equals(currentValue, targetValue)) {
            diffs.add(new MetadataDtos.MetadataDiffItem(field, currentValue, targetValue));
        }
    }

    private void applySnapshotToTrack(Track track, MetadataSnapshot snapshot) {
        track.title = clean(snapshot.title());
        track.normalizedTitle = track.title == null ? null : track.title.toLowerCase(Locale.ROOT);
        track.artist = clean(snapshot.artist());
        track.album = clean(snapshot.album());
    }

    private MetadataSnapshot comparableSnapshot(MetadataSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new MetadataSnapshot(snapshot.title(), snapshot.artist(), snapshot.album(), null, null, null, null, null);
    }

    private List<String> comparableFields(List<String> fields) {
        if (fields == null) {
            return List.of();
        }
        return fields.stream()
                .filter(field -> "title".equals(field) || "artist".equals(field) || "album".equals(field))
                .toList();
    }

    private String musicTitle(Long musicId) {
        TrackFile trackFile = musicId == null ? null : TrackFile.findById(musicId);
        if (trackFile == null || trackFile.trackId == null) {
            return null;
        }
        Track track = Track.findById(trackFile.trackId);
        return track == null ? null : track.title;
    }

    private boolean keywordMatches(MetadataAuditListItem item, String keyword) {
        if (isBlank(keyword)) {
            return true;
        }
        String needle = keyword.toLowerCase(Locale.ROOT);
        return Stream.of(item.musicTitle(), item.filePath(), item.errorMessage(), item.batchId())
                .filter(Objects::nonNull)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(value -> value.contains(needle));
    }

    private List<Long> validateAuditIds(List<Long> auditIds) {
        if (auditIds == null || auditIds.isEmpty()) {
            throw new BadRequestException("auditIds must not be empty");
        }
        if (auditIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("auditIds must not contain null");
        }
        if (auditIds.size() > MAX_BATCH_SIZE) {
            throw new BadRequestException("auditIds size must be less than or equal to " + MAX_BATCH_SIZE);
        }
        return auditIds.stream().distinct().toList();
    }

    private int normalizePageSize(Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize metadata audit payload", exception);
        }
    }

    private String errorMessage(Exception exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private RuntimeException toApiException(Exception exception, String message) {
        if (exception instanceof NotFoundException notFoundException) {
            return notFoundException;
        }
        if (exception instanceof BadRequestException badRequestException) {
            return badRequestException;
        }
        if (exception instanceof ConflictException conflictException) {
            return conflictException;
        }
        return new ConflictException(message);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        return left != null && right != null && left.equalsIgnoreCase(right);
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
