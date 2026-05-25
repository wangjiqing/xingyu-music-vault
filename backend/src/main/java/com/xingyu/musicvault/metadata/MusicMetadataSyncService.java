package com.xingyu.musicvault.metadata;

import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataCompareRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataSyncRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataSyncResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditCreateRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataCompareResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataCompareSnapshot;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataDiffItem;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSnapshot;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSyncResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@ApplicationScoped
public class MusicMetadataSyncService {
    private static final Logger LOG = Logger.getLogger(MusicMetadataSyncService.class);
    public static final int MAX_BATCH_SIZE = 100;
    public static final String MODE_OVERWRITE = "overwrite";
    public static final String DIRECTION_FILE_TO_DB = "file_to_db";
    public static final String DIRECTION_DB_TO_FILE = "db_to_file";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final String SOURCE_EMBEDDED_TAG = "embedded_tag";
    private static final String SOURCE_DATABASE = "database";
    private static final String OPERATION_APPLY = "APPLY";

    @Inject
    AudioMetadataService audioMetadataService;

    @Inject
    MetadataAuditService metadataAuditService;

    public MetadataCompareResponse compare(Long musicId) {
        TrackFile trackFile = findMusic(musicId);
        MetadataSnapshot database = databaseSnapshot(trackFile);
        MetadataSnapshot embedded = readFileSnapshot(trackFile);
        return new MetadataCompareResponse(trackFile.id, compareSnapshot(database), compareSnapshot(embedded),
                diffs(database, embedded), STATUS_SUCCESS, null);
    }

    public List<MetadataCompareResponse> compareBatch(BatchMetadataCompareRequest request) {
        List<Long> ids = validateBatchIds(request == null ? null : request.musicIds());
        return ids.stream().map(this::compareBatchItem).toList();
    }

    @Transactional
    public MetadataSyncResult applyFileToDatabase(Long musicId, MetadataDtos.MetadataSyncRequest request) {
        validateConfirm(request == null ? null : request.confirm());
        return applyFileToDatabase(musicId, null, normalizeMode(request == null ? null : request.mode()));
    }

    @Transactional
    public MetadataSyncResult applyDatabaseToFile(Long musicId, MetadataDtos.MetadataSyncRequest request) {
        validateConfirm(request == null ? null : request.confirm());
        return applyDatabaseToFile(musicId, null, normalizeMode(request == null ? null : request.mode()));
    }

    @Transactional
    public BatchMetadataSyncResponse applyFileToDatabaseBatch(BatchMetadataSyncRequest request) {
        return applyBatch(request, DIRECTION_FILE_TO_DB);
    }

    @Transactional
    public BatchMetadataSyncResponse applyDatabaseToFileBatch(BatchMetadataSyncRequest request) {
        return applyBatch(request, DIRECTION_DB_TO_FILE);
    }

    private BatchMetadataSyncResponse applyBatch(BatchMetadataSyncRequest request, String direction) {
        List<Long> ids = validateBatchIds(request == null ? null : request.musicIds());
        validateConfirm(request == null ? null : request.confirm());
        String mode = normalizeMode(request == null ? null : request.mode());
        String batchId = UUID.randomUUID().toString();
        List<MetadataSyncResult> items = new ArrayList<>();
        int success = 0;
        for (Long id : ids) {
            MetadataSyncResult result = DIRECTION_FILE_TO_DB.equals(direction)
                    ? applyFileToDatabase(id, batchId, mode)
                    : applyDatabaseToFile(id, batchId, mode);
            items.add(result);
            if (STATUS_SUCCESS.equals(result.status())) {
                success++;
            }
        }
        return new BatchMetadataSyncResponse(batchId, ids.size(), success, ids.size() - success, items);
    }

    private MetadataSyncResult applyFileToDatabase(Long musicId, String batchId, String mode) {
        TrackFile trackFile = null;
        MetadataSnapshot beforeDatabase = null;
        MetadataSnapshot beforeFile = null;
        List<String> changedFields = List.of();
        try {
            trackFile = findMusic(musicId);
            beforeDatabase = databaseSnapshot(trackFile);
            beforeFile = readFileSnapshot(trackFile);
            changedFields = changedFields(beforeDatabase, beforeFile);
            Track track = ensureTrack(trackFile);
            applySnapshotToTrack(track, beforeFile);
            if (!changedFields.isEmpty()) {
                track.metadataUpdatedAt = LocalDateTime.now();
                track.metadataExtractedAt = track.metadataUpdatedAt;
                track.metadataSource = SOURCE_EMBEDDED_TAG;
                track.metadataStatus = "synced";
            }
            MetadataSnapshot afterDatabase = databaseSnapshot(trackFile, track);
            MusicMetadataSyncAudit audit = audit(
                    batchId,
                    trackFile,
                    DIRECTION_FILE_TO_DB,
                    SOURCE_EMBEDDED_TAG,
                    SOURCE_DATABASE,
                    mode,
                    beforeDatabase,
                    afterDatabase,
                    beforeFile,
                    beforeFile,
                    changedFields,
                    STATUS_SUCCESS,
                    null
            );
            return new MetadataSyncResult(trackFile.id, DIRECTION_FILE_TO_DB, mode, STATUS_SUCCESS,
                    beforeDatabase, afterDatabase, beforeFile, beforeFile, changedFields, audit.id, null);
        } catch (Exception exception) {
            restoreDatabaseSnapshot(trackFile, beforeDatabase);
            return failedResult(batchId, musicId, trackFile, DIRECTION_FILE_TO_DB, SOURCE_EMBEDDED_TAG, SOURCE_DATABASE,
                    mode, beforeDatabase, null, beforeFile, beforeFile, changedFields, exception);
        }
    }

    private MetadataSyncResult applyDatabaseToFile(Long musicId, String batchId, String mode) {
        TrackFile trackFile = null;
        MetadataSnapshot beforeDatabase = null;
        MetadataSnapshot beforeFile = null;
        MetadataSnapshot afterFile = null;
        boolean fileWriteCompleted = false;
        List<String> changedFields = List.of();
        try {
            trackFile = findMusic(musicId);
            beforeDatabase = databaseSnapshot(trackFile);
            beforeFile = readFileSnapshot(trackFile);
            changedFields = changedFields(beforeDatabase, beforeFile);
            afterFile = audioMetadataService.write(Path.of(trackFile.filePath), beforeDatabase);
            fileWriteCompleted = true;
            MusicMetadataSyncAudit audit = audit(
                    batchId,
                    trackFile,
                    DIRECTION_DB_TO_FILE,
                    SOURCE_DATABASE,
                    SOURCE_EMBEDDED_TAG,
                    mode,
                    beforeDatabase,
                    beforeDatabase,
                    beforeFile,
                    afterFile,
                    changedFields,
                    STATUS_SUCCESS,
                    null
            );
            return new MetadataSyncResult(trackFile.id, DIRECTION_DB_TO_FILE, mode, STATUS_SUCCESS,
                    beforeDatabase, beforeDatabase, beforeFile, afterFile, changedFields, audit.id, null);
        } catch (Exception exception) {
            Exception reportedException = exception;
            if (fileWriteCompleted && trackFile != null) {
                String message = "Audio file metadata was modified but audit record failed to persist: "
                        + errorMessage(exception);
                LOG.errorf(
                        exception,
                        "Database-to-file metadata sync failed after audio file was already modified: musicId=%d filePath=%s message=%s",
                        trackFile.id,
                        trackFile.filePath,
                        message
                );
                reportedException = new AudioMetadataException(message, exception);
            }
            return failedResult(batchId, musicId, trackFile, DIRECTION_DB_TO_FILE, SOURCE_DATABASE, SOURCE_EMBEDDED_TAG,
                    mode, beforeDatabase, beforeDatabase, beforeFile, afterFile, changedFields, reportedException);
        }
    }

    private MetadataCompareResponse compareBatchItem(Long musicId) {
        try {
            return compare(musicId);
        } catch (Exception exception) {
            return new MetadataCompareResponse(musicId, null, null, List.of(), STATUS_FAILED, errorMessage(exception));
        }
    }

    private MetadataSyncResult failedResult(
            String batchId,
            Long musicId,
            TrackFile trackFile,
            String direction,
            String sourceType,
            String targetType,
            String mode,
            MetadataSnapshot beforeDatabase,
            MetadataSnapshot afterDatabase,
            MetadataSnapshot beforeFile,
            MetadataSnapshot afterFile,
            List<String> changedFields,
            Exception exception
    ) {
        String message = errorMessage(exception);
        Long auditId = null;
        if (trackFile != null || batchId != null) {
            try {
                MusicMetadataSyncAudit audit = audit(batchId, trackFile, musicId, direction, sourceType, targetType, mode,
                        beforeDatabase, afterDatabase, beforeFile, afterFile, changedFields, STATUS_FAILED, message);
                auditId = audit.id;
            } catch (RuntimeException auditException) {
                LOG.errorf(
                        auditException,
                        "Failed to persist metadata sync failure audit: musicId=%d direction=%s originalMessage=%s",
                        musicId,
                        direction,
                        message
                );
                message = message + "; additionally failed to persist failure audit: " + errorMessage(auditException);
            }
        }
        if (batchId == null && trackFile == null) {
            throw toApiException(exception, message);
        }
        return new MetadataSyncResult(musicId, direction, mode, STATUS_FAILED,
                beforeDatabase, afterDatabase, beforeFile, afterFile, changedFields, auditId, message);
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

    private MusicMetadataSyncAudit audit(
            String batchId,
            TrackFile trackFile,
            String direction,
            String sourceType,
            String targetType,
            String mode,
            MetadataSnapshot beforeDatabase,
            MetadataSnapshot afterDatabase,
            MetadataSnapshot beforeFile,
            MetadataSnapshot afterFile,
            List<String> changedFields,
            String status,
            String errorMessage
    ) {
        return audit(batchId, trackFile, trackFile.id, direction, sourceType, targetType, mode, beforeDatabase,
                afterDatabase, beforeFile, afterFile, changedFields, status, errorMessage);
    }

    private MusicMetadataSyncAudit audit(
            String batchId,
            TrackFile trackFile,
            Long musicId,
            String direction,
            String sourceType,
            String targetType,
            String mode,
            MetadataSnapshot beforeDatabase,
            MetadataSnapshot afterDatabase,
            MetadataSnapshot beforeFile,
            MetadataSnapshot afterFile,
            List<String> changedFields,
            String status,
            String errorMessage
    ) {
        return metadataAuditService.create(new MetadataAuditCreateRequest(
                batchId,
                musicId,
                trackFile == null ? null : trackFile.filePath,
                direction,
                sourceType,
                targetType,
                mode,
                OPERATION_APPLY,
                beforeDatabase,
                afterDatabase,
                beforeFile,
                afterFile,
                changedFields,
                status,
                errorMessage,
                null,
                LocalDateTime.now(),
                "api"
        ));
    }

    public MetadataSnapshot databaseSnapshot(TrackFile trackFile) {
        return databaseSnapshot(trackFile, trackOf(trackFile));
    }

    public MetadataSnapshot databaseSnapshot(TrackFile trackFile, Track track) {
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

    public MetadataSnapshot readFileSnapshot(TrackFile trackFile) {
        if (trackFile.filePath == null || trackFile.filePath.isBlank()) {
            throw new AudioMetadataException("Audio file path is required");
        }
        return audioMetadataService.read(Path.of(trackFile.filePath));
    }

    public List<MetadataDiffItem> diffs(MetadataSnapshot database, MetadataSnapshot embedded) {
        List<MetadataDiffItem> diffs = new ArrayList<>();
        addDiff(diffs, "title", database.title(), embedded.title());
        addDiff(diffs, "artist", database.artist(), embedded.artist());
        addDiff(diffs, "album", database.album(), embedded.album());
        return diffs;
    }

    private List<String> changedFields(MetadataSnapshot database, MetadataSnapshot embedded) {
        return diffs(database, embedded).stream().map(MetadataDiffItem::field).toList();
    }

    private void addDiff(List<MetadataDiffItem> diffs, String field, Object databaseValue, Object embeddedValue) {
        if (!Objects.equals(databaseValue, embeddedValue)) {
            diffs.add(new MetadataDiffItem(field, databaseValue, embeddedValue));
        }
    }

    public void applySnapshotToTrack(Track track, MetadataSnapshot snapshot) {
        track.title = clean(snapshot.title());
        track.normalizedTitle = track.title == null ? null : track.title.toLowerCase(java.util.Locale.ROOT);
        track.artist = clean(snapshot.artist());
        track.album = clean(snapshot.album());
    }

    private void restoreDatabaseSnapshot(TrackFile trackFile, MetadataSnapshot snapshot) {
        if (trackFile == null || snapshot == null) {
            return;
        }
        Track track = trackOf(trackFile);
        if (track != null) {
            applySnapshotToTrack(track, snapshot);
        }
    }

    private MetadataCompareSnapshot compareSnapshot(MetadataSnapshot snapshot) {
        return new MetadataCompareSnapshot(snapshot.title(), snapshot.artist(), snapshot.album());
    }

    private Track ensureTrack(TrackFile trackFile) {
        Track track = trackOf(trackFile);
        if (track == null) {
            track = new Track();
            track.persist();
            trackFile.trackId = track.id;
        }
        return track;
    }

    private Track trackOf(TrackFile trackFile) {
        if (trackFile == null || trackFile.trackId == null) {
            return null;
        }
        return Track.findById(trackFile.trackId);
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

    private List<Long> validateBatchIds(List<Long> musicIds) {
        if (musicIds == null || musicIds.isEmpty()) {
            throw new BadRequestException("musicIds must not be empty");
        }
        if (musicIds.stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("musicIds must not contain null");
        }
        if (musicIds.size() > MAX_BATCH_SIZE) {
            throw new BadRequestException("musicIds size must be less than or equal to " + MAX_BATCH_SIZE);
        }
        Set<Long> seen = new HashSet<>();
        boolean hasDuplicate = musicIds.stream().anyMatch(id -> !seen.add(id));
        if (hasDuplicate) {
            throw new BadRequestException("musicIds must not contain duplicate IDs");
        }
        return musicIds;
    }

    private void validateConfirm(Boolean confirm) {
        if (!Boolean.TRUE.equals(confirm)) {
            throw new BadRequestException("confirm must be true");
        }
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return MODE_OVERWRITE;
        }
        String value = mode.trim().toLowerCase(java.util.Locale.ROOT);
        if (!MODE_OVERWRITE.equals(value)) {
            throw new BadRequestException("mode must be overwrite");
        }
        return value;
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
