package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.alignment.LyricAlignmentJob;
import com.xingyu.musicvault.alignment.LyricDraft;
import com.xingyu.musicvault.library.TrackFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class SongLyricStatusService {
    private static final String DELETE_STATUS_ACTIVE = "active";
    private static final Set<String> RUNNING_JOB_STATUSES = Set.of("CREATING", "QUEUED", "RUNNING");
    private static final Set<String> FAILED_JOB_STATUSES = Set.of("FAILED");
    private static final Set<String> PENDING_DRAFT_STATUSES = Set.of("PENDING", "EDITING", "READY", "NEEDS_REVIEW");

    @Inject
    EntityManager entityManager;

    public SongLyricStatusSnapshot snapshotForSong(Long musicId) {
        return snapshotsForSongs(List.of(musicId)).getOrDefault(
                musicId,
                new SongLyricStatusSnapshot(musicId, SongLyricStatus.NO_LYRICS, null, false, false)
        );
    }

    public Map<Long, SongLyricStatusSnapshot> snapshotsForSongs(List<Long> musicIds) {
        if (musicIds == null || musicIds.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = musicIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }

        Map<Long, SongLyric> primaryBindings = SongLyric.<SongLyric>list(
                "songId in ?1 and isPrimary = true",
                ids
        ).stream().collect(Collectors.toMap(binding -> binding.songId, Function.identity(), (left, right) -> left));

        Map<Long, Lyric> lyricsById = primaryBindings.values().stream()
                .map(binding -> binding.lyricId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.collectingAndThen(Collectors.toList(), lyricIds -> {
                    if (lyricIds.isEmpty()) {
                        return Map.of();
                    }
                    return Lyric.<Lyric>list("id in ?1", lyricIds).stream()
                            .collect(Collectors.toMap(lyric -> lyric.id, Function.identity()));
                }));

        Set<Long> runningSongIds = LyricAlignmentJob.<LyricAlignmentJob>list(
                "songId in ?1 and status in ?2",
                ids,
                RUNNING_JOB_STATUSES
        ).stream().map(job -> job.songId).collect(Collectors.toSet());

        Set<Long> pendingDraftSongIds = LyricDraft.<LyricDraft>list(
                "musicId in ?1 and draftStatus in ?2",
                ids,
                PENDING_DRAFT_STATUSES
        ).stream().map(draft -> draft.musicId).collect(Collectors.toSet());

        Set<Long> failedSongIds = latestFailedSongIds(ids);

        Map<Long, SongLyricStatusSnapshot> result = new HashMap<>();
        for (Long id : ids) {
            SongLyric binding = primaryBindings.get(id);
            Lyric lyric = binding == null ? null : lyricsById.get(binding.lyricId);
            boolean hasSwlrc = hasUsableSwlrc(lyric);
            boolean hasLrc = hasUsableLrc(lyric);
            SongLyricStatus status;
            if (runningSongIds.contains(id)) {
                status = SongLyricStatus.ALIGNMENT_RUNNING;
            } else if (pendingDraftSongIds.contains(id)) {
                status = SongLyricStatus.DRAFT_PENDING;
            } else if (hasSwlrc) {
                status = SongLyricStatus.SWLRC_READY;
            } else if (hasLrc) {
                status = SongLyricStatus.LRC_READY;
            } else if (failedSongIds.contains(id)) {
                status = SongLyricStatus.FAILED;
            } else {
                status = SongLyricStatus.NO_LYRICS;
            }
            result.put(id, new SongLyricStatusSnapshot(id, status, lyric == null ? null : lyric.id, hasLrc, hasSwlrc));
        }
        return result;
    }

    public List<Long> activeMusicIdsForFilter(String lyricStatusFilter) {
        List<Long> activeIds = activeMusicIds();
        if (lyricStatusFilter == null || lyricStatusFilter.isBlank() || "ALL".equalsIgnoreCase(lyricStatusFilter)) {
            return activeIds;
        }
        String filter = lyricStatusFilter.trim().toUpperCase();
        Set<Long> activeIdSet = new HashSet<>(activeIds);
        List<Long> swlrcReady = List.of();
        List<Long> lrcReady = List.of();
        List<Long> running = List.of();
        List<Long> draftPending = List.of();
        List<Long> failed = List.of();

        if ("SWLRC_READY".equals(filter) || "MISSING_SWLRC".equals(filter) || "NO_LYRICS".equals(filter)) {
            swlrcReady = exactIds(swlrcCandidateIds(activeIdSet), "SWLRC_READY");
        }
        if ("LRC_READY".equals(filter) || "MISSING_SWLRC".equals(filter) || "NO_LYRICS".equals(filter)) {
            lrcReady = exactIds(lrcCandidateIds(activeIdSet), "LRC_READY");
        }
        if ("ALIGNMENT_RUNNING".equals(filter) || "MISSING_SWLRC".equals(filter) || "NO_LYRICS".equals(filter)) {
            running = exactIds(runningCandidateIds(activeIdSet), "ALIGNMENT_RUNNING");
        }
        if ("DRAFT_PENDING".equals(filter) || "MISSING_SWLRC".equals(filter) || "NO_LYRICS".equals(filter)) {
            draftPending = exactIds(draftCandidateIds(activeIdSet), "DRAFT_PENDING");
        }
        if ("FAILED".equals(filter) || "MISSING_SWLRC".equals(filter) || "NO_LYRICS".equals(filter)) {
            failed = exactIds(failedCandidateIds(activeIdSet), "FAILED");
        }

        return switch (filter) {
            case "SWLRC_READY" -> swlrcReady;
            case "LRC_READY" -> lrcReady;
            case "ALIGNMENT_RUNNING" -> running;
            case "DRAFT_PENDING" -> draftPending;
            case "FAILED" -> failed;
            case "NO_LYRICS" -> minus(activeIds, swlrcReady, lrcReady, running, draftPending, failed);
            case "MISSING_SWLRC" -> {
                LinkedHashSet<Long> missing = new LinkedHashSet<>(lrcReady);
                missing.addAll(minus(activeIds, swlrcReady, lrcReady, running, draftPending, failed));
                yield new ArrayList<>(missing);
            }
            default -> snapshotsForSongs(activeIds).values().stream()
                    .filter(snapshot -> matchesFilter(snapshot, filter))
                    .map(SongLyricStatusSnapshot::musicId)
                    .toList();
        };
    }

    public boolean candidateForMissingSwlrc(TrackFile trackFile, SongLyricStatusSnapshot snapshot) {
        return trackFile != null
                && musicFileUsable(trackFile)
                && snapshot != null
                && (snapshot.lyricStatus() == SongLyricStatus.LRC_READY || snapshot.lyricStatus() == SongLyricStatus.NO_LYRICS);
    }

    public boolean musicFileUsable(TrackFile trackFile) {
        return trackFile != null
                && (trackFile.deleteStatus == null || DELETE_STATUS_ACTIVE.equals(trackFile.deleteStatus))
                && hasText(trackFile.filePath)
                && Files.isRegularFile(Path.of(trackFile.filePath));
    }

    private boolean matchesFilter(SongLyricStatusSnapshot snapshot, String filter) {
        if ("MISSING_SWLRC".equals(filter)) {
            return snapshot.lyricStatus() == SongLyricStatus.LRC_READY
                    || snapshot.lyricStatus() == SongLyricStatus.NO_LYRICS;
        }
        return snapshot.lyricStatus().name().equals(filter);
    }

    private List<Long> activeMusicIds() {
        return TrackFile.<TrackFile>list(
                "deleteStatus is null or deleteStatus = ?1",
                DELETE_STATUS_ACTIVE
        ).stream()
                .map(trackFile -> trackFile.id)
                .toList();
    }

    private List<Long> exactIds(List<Long> candidateIds, String filter) {
        return snapshotsForSongs(candidateIds).values().stream()
                .filter(snapshot -> matchesFilter(snapshot, filter))
                .map(SongLyricStatusSnapshot::musicId)
                .toList();
    }

    private List<Long> swlrcCandidateIds(Set<Long> activeIds) {
        return activeOnly(entityManager.createQuery("""
                        select distinct sl.songId
                        from SongLyric sl, Lyric l
                        where sl.lyricId = l.id
                          and sl.isPrimary = true
                          and l.swlrcPath is not null
                          and trim(l.swlrcPath) <> ''
                          and l.swlrcHash is not null
                          and trim(l.swlrcHash) <> ''
                        """, Long.class)
                .getResultList(), activeIds);
    }

    private List<Long> lrcCandidateIds(Set<Long> activeIds) {
        return activeOnly(entityManager.createQuery("""
                        select distinct sl.songId
                        from SongLyric sl, Lyric l
                        where sl.lyricId = l.id
                          and sl.isPrimary = true
                          and l.content is not null
                          and trim(l.content) <> ''
                        """, Long.class)
                .getResultList(), activeIds);
    }

    private List<Long> runningCandidateIds(Set<Long> activeIds) {
        return activeOnly(entityManager.createQuery("""
                        select distinct job.songId
                        from LyricAlignmentJob job
                        where job.status in :statuses
                        """, Long.class)
                .setParameter("statuses", RUNNING_JOB_STATUSES)
                .getResultList(), activeIds);
    }

    private List<Long> draftCandidateIds(Set<Long> activeIds) {
        return activeOnly(entityManager.createQuery("""
                        select distinct draft.musicId
                        from LyricDraft draft
                        where draft.draftStatus in :statuses
                        """, Long.class)
                .setParameter("statuses", PENDING_DRAFT_STATUSES)
                .getResultList(), activeIds);
    }

    private List<Long> failedCandidateIds(Set<Long> activeIds) {
        return activeOnly(entityManager.createQuery("""
                        select distinct job.songId
                        from LyricAlignmentJob job
                        where job.status in :statuses
                           or job.workerOutcome = 'FAILED'
                           or job.importStatus = 'FAILED'
                        """, Long.class)
                .setParameter("statuses", FAILED_JOB_STATUSES)
                .getResultList(), activeIds);
    }

    @SafeVarargs
    private final List<Long> minus(List<Long> baseIds, List<Long>... excludedLists) {
        Set<Long> excluded = new HashSet<>();
        for (List<Long> excludedList : excludedLists) {
            excluded.addAll(excludedList);
        }
        return baseIds.stream()
                .filter(id -> !excluded.contains(id))
                .toList();
    }

    private List<Long> activeOnly(List<Long> ids, Set<Long> activeIds) {
        return ids.stream()
                .filter(activeIds::contains)
                .distinct()
                .toList();
    }

    private Set<Long> latestFailedSongIds(List<Long> ids) {
        Map<Long, LyricAlignmentJob> latestBySong = new HashMap<>();
        for (LyricAlignmentJob job : LyricAlignmentJob.<LyricAlignmentJob>list("songId in ?1", ids)) {
            LyricAlignmentJob current = latestBySong.get(job.songId);
            if (current == null || updatedAt(job).isAfter(updatedAt(current))) {
                latestBySong.put(job.songId, job);
            }
        }
        return latestBySong.values().stream()
                .filter(job -> FAILED_JOB_STATUSES.contains(job.status)
                        || "FAILED".equals(job.workerOutcome)
                        || "FAILED".equals(job.importStatus))
                .map(job -> job.songId)
                .collect(Collectors.toSet());
    }

    private LocalDateTime updatedAt(LyricAlignmentJob job) {
        if (job.updatedAt != null) {
            return job.updatedAt;
        }
        if (job.createdAt != null) {
            return job.createdAt;
        }
        return LocalDateTime.MIN;
    }

    private boolean hasUsableSwlrc(Lyric lyric) {
        return lyric != null
                && hasText(lyric.swlrcPath)
                && hasText(lyric.swlrcHash)
                && Files.isRegularFile(Path.of(lyric.swlrcPath));
    }

    private boolean hasUsableLrc(Lyric lyric) {
        if (lyric == null || !hasText(lyric.content)) {
            return false;
        }
        if (hasText(lyric.sourcePath) && !Files.isRegularFile(Path.of(lyric.sourcePath))) {
            return false;
        }
        return "LRC".equalsIgnoreCase(lyric.format) || hasText(lyric.sourceTaskId) || hasText(lyric.sourceType);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
