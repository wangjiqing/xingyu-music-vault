package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.library.MusicDtos.MusicResponse;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.DailyRecommendationItem;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.DailyRecommendationResponse;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.LyricOverviewResponse;
import com.xingyu.musicvault.lyrics.LyricDashboardDtos.RandomRecommendationResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@ApplicationScoped
public class LyricDashboardService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_STARTED = "STARTED";
    private static final String STATUS_SKIPPED_TODAY = "SKIPPED_TODAY";
    private static final String STATUS_REPLACED = "REPLACED";
    private static final int DAILY_SLOTS = 5;
    private static final int RANDOM_MAX = 20;

    @Inject
    SongLyricStatusService statusService;

    @Inject
    ArtworkService artworkService;

    @Inject
    EntityManager entityManager;

    public LyricOverviewResponse overview() {
        List<TrackFile> active = usableActiveTrackFiles();
        Map<Long, SongLyricStatusSnapshot> snapshots = statusService.snapshotsForSongs(active.stream().map(track -> track.id).toList());
        long total = active.size();
        long swlrc = countStatus(snapshots, SongLyricStatus.SWLRC_READY);
        long lrc = countStatus(snapshots, SongLyricStatus.LRC_READY);
        long noLyrics = countStatus(snapshots, SongLyricStatus.NO_LYRICS);
        long running = countStatus(snapshots, SongLyricStatus.ALIGNMENT_RUNNING);
        long draftPending = countStatus(snapshots, SongLyricStatus.DRAFT_PENDING);
        long withLyrics = swlrc + lrc;
        return new LyricOverviewResponse(
                total,
                withLyrics,
                rate(withLyrics, total),
                swlrc,
                rate(swlrc, total),
                lrc,
                noLyrics,
                running,
                draftPending
        );
    }

    @Transactional
    public DailyRecommendationResponse daily() {
        LocalDate today = LocalDate.now();
        ensureDailyRecommendations(today);
        return new DailyRecommendationResponse(toDailyItems(visibleDailyItems(today)));
    }

    @Transactional
    public DailyRecommendationItem start(Long id) {
        LyricDailyRecommendation item = findRecommendation(id);
        item.actionStatus = STATUS_STARTED;
        item.actedAt = LocalDateTime.now();
        return toDailyItems(List.of(item)).getFirst();
    }

    @Transactional
    public DailyRecommendationResponse skip(Long id) {
        LyricDailyRecommendation item = findRecommendation(id);
        item.actionStatus = STATUS_SKIPPED_TODAY;
        item.actedAt = LocalDateTime.now();
        return daily();
    }

    @Transactional
    public DailyRecommendationItem replace(Long id) {
        LyricDailyRecommendation item = findRecommendation(id);
        LocalDate date = LocalDate.parse(item.recommendationDate);
        List<TrackFile> candidates = candidates(date, Set.of());
        if (candidates.isEmpty()) {
            throw new NotFoundException("No replacement lyric recommendation candidate available");
        }
        TrackFile replacement = candidates.getFirst();
        item.actionStatus = STATUS_REPLACED;
        item.actedAt = LocalDateTime.now();
        entityManager.flush();

        LyricDailyRecommendation created = createRecommendation(date, item.slotNo, replacement);
        item.replacedById = created.id;
        return toDailyItems(List.of(created)).getFirst();
    }

    public RandomRecommendationResponse random(int count) {
        if (count < 1 || count > RANDOM_MAX) {
            throw new BadRequestException("count must be between 1 and 20");
        }
        List<TrackFile> candidates = candidates(null, Set.of(), "random:" + System.nanoTime()).stream()
                .limit(count)
                .toList();
        String message = candidates.isEmpty() ? "当前没有可推荐的待制作歌曲" : null;
        return new RandomRecommendationResponse(toMusicResponses(candidates), message);
    }

    private void ensureDailyRecommendations(LocalDate date) {
        List<LyricDailyRecommendation> activeExisting = LyricDailyRecommendation.list(
                "recommendationDate = ?1 and actionStatus in ?2 order by slotNo asc, createdAt asc",
                date.toString(),
                List.of(STATUS_PENDING, STATUS_STARTED)
        );
        Set<Integer> occupiedSlots = activeExisting.stream()
                .map(item -> item.slotNo)
                .collect(Collectors.toSet());
        if (occupiedSlots.size() >= DAILY_SLOTS) {
            return;
        }
        Set<Long> used = LyricDailyRecommendation.<LyricDailyRecommendation>list(
                "recommendationDate = ?1",
                date.toString()
        ).stream().map(item -> item.musicId).collect(Collectors.toSet());
        List<TrackFile> candidates = candidates(date, used);
        int candidateIndex = 0;
        for (int slot = 1; slot <= DAILY_SLOTS; slot++) {
            if (occupiedSlots.contains(slot)) {
                continue;
            }
            while (candidateIndex < candidates.size()) {
                TrackFile candidate = candidates.get(candidateIndex++);
                if (insertRecommendationIfAbsent(date, slot, candidate)) {
                    used.add(candidate.id);
                    break;
                }
            }
        }
    }

    private List<LyricDailyRecommendation> visibleDailyItems(LocalDate date) {
        List<LyricDailyRecommendation> items = LyricDailyRecommendation.<LyricDailyRecommendation>list(
                "recommendationDate = ?1 and actionStatus in ?2 order by slotNo asc, createdAt asc",
                date.toString(),
                List.of(STATUS_PENDING, STATUS_STARTED)
        );
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> musicIds = items.stream().map(item -> item.musicId).distinct().toList();
        Map<Long, TrackFile> trackFiles = TrackFile.<TrackFile>list("id in ?1", musicIds).stream()
                .collect(Collectors.toMap(trackFile -> trackFile.id, Function.identity(), (left, right) -> left));
        Map<Long, SongLyricStatusSnapshot> snapshots = statusService.snapshotsForSongs(musicIds);
        return items.stream()
                .filter(item -> statusService.candidateForMissingSwlrc(trackFiles.get(item.musicId), snapshots.get(item.musicId)))
                .toList();
    }

    private LyricDailyRecommendation createRecommendation(LocalDate date, int slot, TrackFile candidate) {
        SongLyricStatusSnapshot snapshot = statusService.snapshotForSong(candidate.id);
        LyricDailyRecommendation item = new LyricDailyRecommendation();
        item.recommendationDate = date.toString();
        item.slotNo = slot;
        item.musicId = candidate.id;
        item.recommendationType = snapshot.lyricStatus() == SongLyricStatus.LRC_READY ? "LRC_UPGRADE" : "NO_LYRICS";
        item.actionStatus = STATUS_PENDING;
        item.persistAndFlush();
        return item;
    }

    private boolean insertRecommendationIfAbsent(LocalDate date, int slot, TrackFile candidate) {
        SongLyricStatusSnapshot snapshot = statusService.snapshotForSong(candidate.id);
        String recommendationType = snapshot.lyricStatus() == SongLyricStatus.LRC_READY ? "LRC_UPGRADE" : "NO_LYRICS";
        LocalDateTime now = LocalDateTime.now();
        int inserted = entityManager.createNativeQuery("""
                        insert or ignore into lyric_daily_recommendation
                            (recommendation_date, slot_no, music_id, recommendation_type, action_status, created_at, updated_at)
                        values
                            (?1, ?2, ?3, ?4, ?5, ?6, ?7)
                        """)
                .setParameter(1, date.toString())
                .setParameter(2, slot)
                .setParameter(3, candidate.id)
                .setParameter(4, recommendationType)
                .setParameter(5, STATUS_PENDING)
                .setParameter(6, now)
                .setParameter(7, now)
                .executeUpdate();
        return inserted > 0;
    }

    private List<TrackFile> candidates(LocalDate date, Set<Long> additionallyExcluded) {
        return candidates(date, additionallyExcluded, null);
    }

    private List<TrackFile> candidates(LocalDate date, Set<Long> additionallyExcluded, String seedSuffix) {
        Set<Long> excluded = new HashSet<>(additionallyExcluded);
        if (date != null) {
            LyricDailyRecommendation.<LyricDailyRecommendation>list("recommendationDate = ?1", date.toString())
                    .forEach(item -> excluded.add(item.musicId));
        }
        List<TrackFile> active = usableActiveTrackFiles();
        Map<Long, SongLyricStatusSnapshot> snapshots = statusService.snapshotsForSongs(active.stream().map(track -> track.id).toList());
        String seed = date == null ? "random:" + seedSuffix : date + (seedSuffix == null ? "" : ":" + seedSuffix);
        return active.stream()
                .filter(track -> !excluded.contains(track.id))
                .filter(track -> statusService.candidateForMissingSwlrc(track, snapshots.get(track.id)))
                .sorted(Comparator.comparing(track -> stableKey(seed, track.id)))
                .toList();
    }

    private List<TrackFile> usableActiveTrackFiles() {
        return TrackFile.<TrackFile>list(
                "deleteStatus is null or deleteStatus = ?1",
                "active"
        ).stream().filter(statusService::musicFileUsable).toList();
    }

    private List<DailyRecommendationItem> toDailyItems(List<LyricDailyRecommendation> items) {
        if (items.isEmpty()) {
            return List.of();
        }
        List<Long> musicIds = items.stream().map(item -> item.musicId).distinct().toList();
        Map<Long, TrackFile> trackFileMap = TrackFile.<TrackFile>list("id in ?1", musicIds).stream()
                .collect(Collectors.toMap(trackFile -> trackFile.id, Function.identity(), (left, right) -> left));
        Map<Long, MusicResponse> musicResponses = toMusicResponseMap(new ArrayList<>(trackFileMap.values()));
        return items.stream()
                .map(item -> new DailyRecommendationItem(
                        item.id,
                        item.recommendationDate,
                        item.slotNo,
                        item.recommendationType,
                        item.actionStatus,
                        musicResponses.get(item.musicId)
                ))
                .toList();
    }

    private List<MusicResponse> toMusicResponses(List<TrackFile> trackFiles) {
        Map<Long, MusicResponse> responseMap = toMusicResponseMap(trackFiles);
        return trackFiles.stream()
                .map(trackFile -> responseMap.get(trackFile.id))
                .toList();
    }

    private Map<Long, MusicResponse> toMusicResponseMap(List<TrackFile> trackFiles) {
        if (trackFiles == null || trackFiles.isEmpty()) {
            return Map.of();
        }
        List<Long> musicIds = trackFiles.stream().map(trackFile -> trackFile.id).distinct().toList();
        Map<Long, SongLyricStatusSnapshot> lyrics = statusService.snapshotsForSongs(musicIds);
        Map<Long, ArtworkService.PrimaryArtworkSummary> artworks = artworkService.primaryArtworkForMusicIds(musicIds);
        List<Long> trackIds = trackFiles.stream()
                .map(trackFile -> trackFile.trackId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, Track> tracks = trackIds.isEmpty()
                ? Map.of()
                : Track.<Track>list("id in ?1", trackIds).stream()
                .collect(Collectors.toMap(track -> track.id, Function.identity(), (left, right) -> left));
        return trackFiles.stream()
                .collect(Collectors.toMap(trackFile -> trackFile.id, trackFile -> {
                    SongLyricStatusSnapshot lyric = lyrics.getOrDefault(
                            trackFile.id,
                            new SongLyricStatusSnapshot(trackFile.id, SongLyricStatus.NO_LYRICS, null, false, false)
                    );
                    ArtworkService.PrimaryArtworkSummary artwork = artworks.get(trackFile.id);
                    Track track = trackFile.trackId == null ? null : tracks.get(trackFile.trackId);
                    return MusicResponse.from(
                            trackFile,
                            track,
                            lyric.lyricStatus().name(),
                            lyric.lyricId(),
                            lyric.hasLrc(),
                            lyric.hasSwlrc(),
                            artwork == null ? "MISSING" : "BOUND",
                            artwork == null ? null : artwork.artworkId(),
                            artwork == null ? null : artwork.artworkPreviewUrl(),
                            artwork == null ? null : artwork.artworkFileName(),
                            artwork == null ? null : artwork.artworkFileExists()
                    );
                }, (left, right) -> left));
    }

    private LyricDailyRecommendation findRecommendation(Long id) {
        LyricDailyRecommendation item = LyricDailyRecommendation.findById(id);
        if (item == null) {
            throw new NotFoundException("Lyric recommendation not found");
        }
        return item;
    }

    private long countStatus(Map<Long, SongLyricStatusSnapshot> snapshots, SongLyricStatus status) {
        return snapshots.values().stream().filter(snapshot -> snapshot.lyricStatus() == status).count();
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (double) numerator / (double) denominator;
    }

    private String stableKey(String seed, Long musicId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((seed + ":" + musicId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
