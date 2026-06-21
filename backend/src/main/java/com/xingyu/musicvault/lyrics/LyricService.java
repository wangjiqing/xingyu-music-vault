package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.LyricDtos.BoundSongResponse;
import com.xingyu.musicvault.lyrics.LyricDtos.LyricDetailResponse;
import com.xingyu.musicvault.lyrics.LyricDtos.LyricListItemResponse;
import com.xingyu.musicvault.lyrics.LyricDtos.LyricScanResponse;
import com.xingyu.musicvault.lyrics.LyricDtos.SongLyricResponse;
import com.xingyu.musicvault.openapi.OpenApiChangeLogService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.QuarkusTransactionException;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class LyricService {
    private static final Logger LOG = Logger.getLogger(LyricService.class);
    private static final Pattern LRC_TAG = Pattern.compile("^\\s*\\[(ti|ar|al):([^]]*)]\\s*$", Pattern.CASE_INSENSITIVE);
    private static final Set<Path> UNSAFE_ROOTS = Set.of(
            Paths.get("/"),
            Paths.get("/etc"),
            Paths.get("/Users"),
            Paths.get("/home")
    );

    @Inject
    MusicVaultConfig config;

    @Inject
    LyricRepository lyricRepository;

    @Inject
    SongLyricRepository songLyricRepository;

    @Inject
    OpenApiChangeLogService openApiChangeLogService;

    public LyricScanResponse scan(String requestedPath, boolean overwritePrimary) {
        Path root = resolveScanRoot(requestedPath);
        ScanCounters counters = new ScanCounters();
        LOG.infof("Scanning lyric directory: root=%s overwritePrimary=%s", root, overwritePrimary);

        Set<Path> existingLyricFiles = new HashSet<>();
        try (Stream<Path> paths = Files.walk(root)) {
            List<Path> lrcFiles = paths.filter(Files::isRegularFile)
                    .filter(this::isLrcFile)
                    .toList();
            existingLyricFiles.addAll(lrcFiles.stream().map(this::realOrNormalizedPath).collect(Collectors.toSet()));
            lrcFiles.forEach(path -> scanFile(path, overwritePrimary, counters));
        } catch (IOException | UncheckedIOException exception) {
            throw new BadRequestException("Failed to scan lyric directory: " + exception.getMessage(), exception);
        }

        if (counters.failed == 0) {
            try {
                QuarkusTransaction.requiringNew().run(() -> synchronizeDeletedSourceFiles(root, existingLyricFiles));
            } catch (QuarkusTransactionException exception) {
                LOG.errorf(exception, "Failed to synchronize deleted source files after successful lyric scan: root=%s", root);
            }
        } else {
            LOG.warnf(
                    "Skip deleted lyric source synchronization because lyric scan had file failures: root=%s failed=%d",
                    root,
                    counters.failed
            );
        }

        return new LyricScanResponse(
                root.toString(),
                counters.totalFiles,
                counters.imported,
                counters.duplicateFiles,
                counters.matched,
                counters.unmatched,
                counters.skippedBindings,
                counters.failed
        );
    }

    public PageResponse<LyricListItemResponse> listLyrics(
            Integer page,
            Integer size,
            String keyword,
            String bindStatus,
            String parseStatus,
            String sourceType
    ) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);
        LyricQuery lyricQuery = lyricQuery(keyword, bindStatus, parseStatus, sourceType);
        if (lyricQuery.emptyResult()) {
            return new PageResponse<>(List.of(), pageValue, sizeValue, 0);
        }

        PanacheQuery<Lyric> query = lyricQuery.query().isBlank()
                ? Lyric.findAll(Sort.descending("createdAt"))
                : Lyric.find(lyricQuery.query(), Sort.descending("createdAt"), lyricQuery.parameters().toArray());
        long total = query.count();
        List<Lyric> lyrics = query.page(Page.of(pageValue, sizeValue)).list();
        Map<Long, BindingSummary> bindingsByLyricId = primaryBindingsByLyricId(
                lyrics.stream().map(lyric -> lyric.id).toList()
        );
        List<LyricListItemResponse> items = lyrics.stream()
                .map(lyric -> toListItem(lyric, bindingsByLyricId.get(lyric.id)))
                .toList();
        return new PageResponse<>(items, pageValue, sizeValue, total);
    }

    public LyricDetailResponse getLyric(Long id) {
        Lyric lyric = lyricRepository.findById(id);
        if (lyric == null) {
            throw new NotFoundException("Lyric not found");
        }

        List<BindingSummary> bindings = bindingsForLyricIds(List.of(id)).getOrDefault(id, List.of());
        BoundSongResponse primary = bindings.stream()
                .filter(binding -> Boolean.TRUE.equals(binding.boundSong().isPrimary()))
                .findFirst()
                .map(BindingSummary::boundSong)
                .orElseGet(() -> bindings.isEmpty() ? null : bindings.getFirst().boundSong());
        List<BoundSongResponse> boundSongs = bindings.stream()
                .map(BindingSummary::boundSong)
                .toList();

        return new LyricDetailResponse(
                lyric.id,
                lyric.title,
                lyric.artist,
                lyric.album,
                lyric.language,
                lyric.releaseYear,
                lyric.sourceType,
                lyric.sourcePath,
                lyric.format,
                lyric.content,
                lyric.contentHash,
                apiParseStatus(lyric.parseStatus),
                lyric.parseMessage,
                boundSongs.isEmpty() ? "UNBOUND" : "BOUND",
                primary,
                boundSongs,
                lyric.createdAt,
                lyric.updatedAt
        );
    }

    public void deleteUnboundLyric(Long id) {
        try {
            QuarkusTransaction.requiringNew().run(() -> {
                Lyric lyric = lyricRepository.findById(id);
                if (lyric == null) {
                    throw new NotFoundException("Lyric not found");
                }
                if (songLyricRepository.hasBindings(id)) {
                    throw new ConflictException("Lyric record is bound to a song and cannot be deleted");
                }
                lyric.delete();
            });
        } catch (QuarkusTransactionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof ConflictException conflictException) {
                throw conflictException;
            }
            if (cause instanceof NotFoundException notFoundException) {
                throw notFoundException;
            }
            throw exception;
        }
    }

    public SongLyricResponse getSongLyric(Long songId) {
        TrackFile trackFile = TrackFile.findById(songId);
        if (trackFile == null) {
            throw new NotFoundException("Song not found");
        }

        SongLyric binding = songLyricRepository.findPrimaryBySongId(songId);
        if (binding == null) {
            return SongLyricResponse.noLyric(songId);
        }
        Lyric lyric = lyricRepository.findById(binding.lyricId);
        if (lyric == null) {
            return SongLyricResponse.noLyric(songId);
        }
        return SongLyricResponse.from(songId, statusOf(lyric).name(), lyric);
    }

    public LyricStatus statusForSong(Long songId) {
        SongLyric binding = songLyricRepository.findPrimaryBySongId(songId);
        if (binding == null) {
            return LyricStatus.NO_LYRIC;
        }
        Lyric lyric = lyricRepository.findById(binding.lyricId);
        if (lyric == null) {
            return LyricStatus.NO_LYRIC;
        }
        return statusOf(lyric);
    }

    public Long primaryLyricIdForSong(Long songId) {
        SongLyric binding = songLyricRepository.findPrimaryBySongId(songId);
        return binding == null ? null : binding.lyricId;
    }

    public Map<Long, PrimaryLyricSummary> primaryLyricsForSongIds(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Map.of();
        }

        List<SongLyric> bindings = songLyricRepository.findPrimaryBySongIds(songIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }

        Map<Long, Lyric> lyricsById = Lyric.<Lyric>list(
                "id in ?1",
                bindings.stream().map(binding -> binding.lyricId).distinct().toList()
        ).stream().collect(Collectors.toMap(lyric -> lyric.id, Function.identity()));

        return bindings.stream()
                .map(binding -> {
                    Lyric lyric = lyricsById.get(binding.lyricId);
                    if (lyric == null) {
                        return null;
                    }
                    return Map.entry(
                            binding.songId,
                            new PrimaryLyricSummary(binding.lyricId, statusOf(lyric).name())
                    );
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private LyricQuery lyricQuery(String keyword, String bindStatus, String parseStatus, String sourceType) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();

        String keywordValue = blankToNull(keyword);
        if (keywordValue != null) {
            int index = parameters.size() + 1;
            conditions.add("(lower(title) like ?%1$d or lower(artist) like ?%1$d or lower(album) like ?%1$d or lower(sourcePath) like ?%1$d)"
                    .formatted(index));
            parameters.add("%" + keywordValue.toLowerCase(Locale.ROOT) + "%");
        }

        String sourceTypeValue = normalizeOptionalFilter(sourceType);
        if (sourceTypeValue != null) {
            conditions.add("sourceType = ?" + (parameters.size() + 1));
            parameters.add(sourceTypeValue);
        }

        String parseStatusValue = apiParseStatusFilter(parseStatus);
        if (parseStatusValue != null) {
            conditions.add(parseStatusValue);
        }

        String bindStatusValue = normalizeBindStatus(bindStatus);
        if (bindStatusValue != null) {
            List<Long> boundLyricIds = songLyricRepository.findDistinctBoundLyricIds();
            if ("BOUND".equals(bindStatusValue)) {
                if (boundLyricIds.isEmpty()) {
                    return new LyricQuery("", List.of(), true);
                }
                conditions.add("id in ?" + (parameters.size() + 1));
                parameters.add(boundLyricIds);
            } else {
                if (!boundLyricIds.isEmpty()) {
                    conditions.add("id not in ?" + (parameters.size() + 1));
                    parameters.add(boundLyricIds);
                }
            }
        }

        return new LyricQuery(String.join(" and ", conditions), parameters, false);
    }

    private String apiParseStatusFilter(String parseStatus) {
        String value = normalizeOptionalFilter(parseStatus);
        if (value == null) {
            return null;
        }
        return switch (value) {
            case "SUCCESS", "PARSED" -> "parseStatus = 'PARSED'";
            case "FAILED", "PARSE_FAILED" -> "parseStatus = 'PARSE_FAILED'";
            case "UNKNOWN" -> "(parseStatus is null or parseStatus not in ('PARSED', 'PARSE_FAILED'))";
            default -> throw new BadRequestException("parseStatus must be SUCCESS, FAILED, UNKNOWN, PARSED, or PARSE_FAILED");
        };
    }

    private String normalizeBindStatus(String bindStatus) {
        String value = normalizeOptionalFilter(bindStatus);
        if (value == null) {
            return null;
        }
        if (!"BOUND".equals(value) && !"UNBOUND".equals(value)) {
            throw new BadRequestException("bindStatus must be BOUND or UNBOUND");
        }
        return value;
    }

    private String normalizeOptionalFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private Map<Long, BindingSummary> primaryBindingsByLyricId(List<Long> lyricIds) {
        return bindingsForLyricIds(lyricIds).entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), entry.getValue().getFirst()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Map<Long, List<BindingSummary>> bindingsForLyricIds(List<Long> lyricIds) {
        List<SongLyric> bindings = songLyricRepository.findByLyricIds(lyricIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }

        Map<Long, TrackFile> trackFilesById = trackFilesById(bindings.stream()
                .map(binding -> binding.songId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        Map<Long, Track> tracksById = tracksById(trackFilesById.values().stream()
                .map(trackFile -> trackFile.trackId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());

        Map<Long, List<BindingSummary>> result = new HashMap<>();
        for (SongLyric binding : bindings) {
            TrackFile trackFile = trackFilesById.get(binding.songId);
            Track track = trackFile == null || trackFile.trackId == null ? null : tracksById.get(trackFile.trackId);
            String songTitle = track == null ? null : track.title;
            if ((songTitle == null || songTitle.isBlank()) && trackFile != null) {
                songTitle = trackFile.fileName;
            }
            BoundSongResponse boundSong = new BoundSongResponse(
                    binding.songId,
                    songTitle,
                    track == null ? null : track.artist,
                    track == null ? null : track.album,
                    trackFile == null ? null : trackFile.fileName,
                    binding.matchType,
                    binding.matchScore,
                    binding.isPrimary
            );
            result.computeIfAbsent(binding.lyricId, ignored -> new ArrayList<>())
                    .add(new BindingSummary(boundSong));
        }
        return result;
    }

    private Map<Long, TrackFile> trackFilesById(List<Long> songIds) {
        if (songIds.isEmpty()) {
            return Map.of();
        }
        return TrackFile.<TrackFile>list("id in ?1", songIds).stream()
                .collect(Collectors.toMap(trackFile -> trackFile.id, Function.identity()));
    }

    private Map<Long, Track> tracksById(List<Long> trackIds) {
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        return Track.<Track>list("id in ?1", trackIds).stream()
                .collect(Collectors.toMap(track -> track.id, Function.identity()));
    }

    private LyricListItemResponse toListItem(Lyric lyric, BindingSummary binding) {
        BoundSongResponse boundSong = binding == null ? null : binding.boundSong();
        return new LyricListItemResponse(
                lyric.id,
                lyric.title,
                lyric.artist,
                lyric.album,
                lyric.language,
                lyric.releaseYear,
                lyric.sourceType,
                lyric.sourcePath,
                lyric.format,
                apiParseStatus(lyric.parseStatus),
                lyric.parseMessage,
                boundSong == null ? "UNBOUND" : "BOUND",
                boundSong == null ? null : boundSong.songId(),
                boundSong == null ? null : boundSong.title(),
                boundSong == null ? null : boundSong.artist(),
                boundSong == null ? null : boundSong.matchType(),
                boundSong == null ? null : boundSong.matchScore(),
                boundSong == null ? null : boundSong.isPrimary(),
                lyric.createdAt,
                lyric.updatedAt
        );
    }

    private String apiParseStatus(String parseStatus) {
        if ("PARSED".equals(parseStatus)) {
            return "SUCCESS";
        }
        if ("PARSE_FAILED".equals(parseStatus)) {
            return "FAILED";
        }
        return "UNKNOWN";
    }

    private void scanFile(Path path, boolean overwritePrimary, ScanCounters counters) {
        counters.totalFiles++;
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | UncheckedIOException exception) {
            counters.failed++;
            LOG.warnf(exception, "Failed to import lyric file: path=%s", path);
            return;
        }

        String contentHash = sha256(content);
        ParsedLyricMetadata metadata = parseMetadata(path, content);
        ScanCounters beforeImport = counters.snapshot();
        try {
            QuarkusTransaction.requiringNew().run(() -> importLyricFile(
                    path,
                    content,
                    contentHash,
                    metadata,
                    overwritePrimary,
                    counters
            ));
        } catch (RuntimeException exception) {
            counters.copyFrom(beforeImport);
            counters.failed++;
            LOG.errorf(exception, "Database error importing lyric file: path=%s", path);
        }
    }

    private void importLyricFile(
            Path path,
            String content,
            String contentHash,
            ParsedLyricMetadata metadata,
            boolean overwritePrimary,
            ScanCounters counters
    ) {
        String normalizedSourcePath = normalizedSourcePath(path);
        List<Lyric> sameSourceLyrics = lyricRepository.findLocalBySourcePath(normalizedSourcePath);
        Lyric lyric = reusableLyricForSourcePath(sameSourceLyrics);
        boolean reusedSourcePath = lyric != null;

        if (reusedSourcePath) {
            deleteUnboundContentHashDuplicate(lyric, contentHash);
            refreshLyricSource(lyric, normalizedSourcePath, content, contentHash, metadata);
            removeUnboundDuplicatesForSourcePath(lyric, normalizedSourcePath);
        } else if ((lyric = lyricRepository.findByContentHash(contentHash)) != null) {
            refreshLyricSource(lyric, normalizedSourcePath, content, contentHash, metadata);
            counters.duplicateFiles++;
        } else {
            lyric = new Lyric();
            lyric.title = metadata.title();
            lyric.artist = metadata.artist();
            lyric.album = metadata.album();
            lyric.sourceType = "LOCAL_FILE";
            lyric.sourcePath = normalizedSourcePath;
            lyric.content = content;
            lyric.contentHash = contentHash;
            lyric.format = "LRC";
            lyric.parseStatus = "PARSED";
            lyric.persist();
            counters.imported++;
        }

        MatchResult match = findBestMatch(metadata);
        if (match == null) {
            counters.unmatched++;
            return;
        }

        if (bind(match.songId(), lyric.id, match.matchType(), match.score(), overwritePrimary)) {
            openApiChangeLogService.recordLyricsChange(match.songId());
            counters.matched++;
        } else {
            counters.skippedBindings++;
        }
    }

    private Lyric reusableLyricForSourcePath(List<Lyric> lyrics) {
        if (lyrics == null || lyrics.isEmpty()) {
            return null;
        }
        return lyrics.stream()
                .filter(lyric -> songLyricRepository.hasBindings(lyric.id))
                .findFirst()
                .orElseGet(lyrics::getFirst);
    }

    private void deleteUnboundContentHashDuplicate(Lyric retainedLyric, String contentHash) {
        Lyric duplicate = lyricRepository.findByContentHash(contentHash);
        if (duplicate == null || Objects.equals(duplicate.id, retainedLyric.id)) {
            return;
        }
        if (!songLyricRepository.hasBindings(duplicate.id)) {
            duplicate.delete();
        }
    }

    private void refreshLyricSource(
            Lyric lyric,
            String normalizedSourcePath,
            String content,
            String contentHash,
            ParsedLyricMetadata metadata
    ) {
        lyric.title = metadata.title();
        lyric.artist = metadata.artist();
        lyric.album = metadata.album();
        lyric.sourceType = "LOCAL_FILE";
        lyric.sourcePath = normalizedSourcePath;
        lyric.content = content;
        lyric.contentHash = contentHash;
        lyric.format = "LRC";
        lyric.parseStatus = "PARSED";
        lyric.parseMessage = null;
    }

    private void removeUnboundDuplicatesForSourcePath(Lyric retainedLyric, String normalizedSourcePath) {
        List<Lyric> duplicates = Lyric.<Lyric>list(
                "sourceType = ?1 and sourcePath = ?2 and id <> ?3",
                "LOCAL_FILE",
                normalizedSourcePath,
                retainedLyric.id
        );
        for (Lyric duplicate : duplicates) {
            if (songLyricRepository.hasBindings(duplicate.id)) {
                continue;
            }
            duplicate.delete();
        }
    }

    private void synchronizeDeletedSourceFiles(Path root, Set<Path> existingLyricFiles) {
        List<Lyric> managedLyrics = Lyric.<Lyric>list("sourceType = ?1 and sourcePath is not null", "LOCAL_FILE").stream()
                .filter(lyric -> sourcePathWithinRoot(lyric.sourcePath, root))
                .toList();
        if (managedLyrics.isEmpty()) {
            return;
        }

        Set<Long> missingLyricIds = managedLyrics.stream()
                .filter(lyric -> !sourceFileExistsInScan(lyric.sourcePath, root, existingLyricFiles))
                .map(lyric -> lyric.id)
                .collect(Collectors.toSet());
        if (missingLyricIds.isEmpty()) {
            return;
        }

        List<SongLyric> bindings = songLyricRepository.findByLyricIds(missingLyricIds.stream().toList());
        if (bindings.isEmpty()) {
            LOG.infof("Found deleted lyric source files with no active bindings: root=%s lyrics=%d", root, missingLyricIds.size());
            return;
        }

        List<Long> changedSongIds = bindings.stream()
                .map(binding -> binding.songId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        for (SongLyric binding : bindings) {
            binding.delete();
        }
        // In the current model, removing the primary lyric binding changes only the track's lyrics resource surface.
        changedSongIds.forEach(openApiChangeLogService::recordLyricsChange);
        LOG.infof(
                "Unbound lyrics with deleted source files: root=%s lyrics=%d bindings=%d songs=%d",
                root,
                missingLyricIds.size(),
                bindings.size(),
                changedSongIds.size()
        );
    }

    private boolean sourcePathWithinRoot(String sourcePath, Path root) {
        return sourcePathCandidates(sourcePath, root).stream().anyMatch(path -> path.startsWith(root));
    }

    private boolean sourceFileExistsInScan(String sourcePath, Path root, Set<Path> existingLyricFiles) {
        return sourcePathCandidates(sourcePath, root).stream().anyMatch(existingLyricFiles::contains);
    }

    private Set<Path> sourcePathCandidates(String sourcePath, Path root) {
        Path path = Path.of(sourcePath);
        Set<Path> candidates = new HashSet<>();
        // Path equality is case-sensitive; when a source exists, toRealPath normalizes to the filesystem's canonical casing.
        candidates.add(realOrNormalizedPath(path));
        if (!path.isAbsolute()) {
            candidates.add(realOrNormalizedPath(root.resolve(path)));
        }
        return candidates;
    }

    private Path realOrNormalizedPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path.toAbsolutePath().normalize();
        }
    }

    private String normalizedSourcePath(Path path) {
        return realOrNormalizedPath(path).toString();
    }

    private boolean bind(Long songId, Long lyricId, String matchType, int matchScore, boolean overwritePrimary) {
        SongLyric existing = songLyricRepository.findBySongIdAndLyricId(songId, lyricId);
        if (existing != null) {
            return false;
        }

        SongLyric primary = songLyricRepository.findPrimaryBySongId(songId);
        if (primary != null && !overwritePrimary) {
            return false;
        }
        if (primary != null) {
            primary.isPrimary = false;
        }

        SongLyric binding = new SongLyric();
        binding.songId = songId;
        binding.lyricId = lyricId;
        binding.matchType = matchType;
        binding.matchScore = matchScore;
        binding.isPrimary = true;
        binding.persist();
        return true;
    }

    private MatchResult findBestMatch(ParsedLyricMetadata metadata) {
        if (metadata.title() == null || metadata.title().isBlank()) {
            return null;
        }

        List<Track> tracks = Track.list("normalizedTitle", normalize(metadata.title()));
        if (tracks.isEmpty()) {
            return null;
        }

        Map<Long, Track> tracksById = tracks.stream()
                .collect(Collectors.toMap(track -> track.id, Function.identity()));
        List<TrackFile> candidates = TrackFile.<TrackFile>list(
                "trackId in ?1",
                tracksById.keySet().stream().toList()
        );
        if (candidates.isEmpty()) {
            return null;
        }
        TrackFile exactArtist = candidates.stream()
                .filter(trackFile -> artistMatches(tracksById.get(trackFile.trackId), metadata.artist()))
                .findFirst()
                .orElse(null);
        if (exactArtist != null) {
            return new MatchResult(exactArtist.id, "TITLE_ARTIST", 100);
        }
        return new MatchResult(candidates.getFirst().id, "TITLE", 80);
    }

    private boolean artistMatches(Track track, String lyricArtist) {
        if (lyricArtist == null || lyricArtist.isBlank()) {
            return false;
        }
        if (track == null || track.artist == null || track.artist.isBlank()) {
            return false;
        }
        return normalize(track.artist).equals(normalize(lyricArtist));
    }

    private LyricStatus statusOf(Lyric lyric) {
        if ("PARSE_FAILED".equals(lyric.parseStatus)) {
            return LyricStatus.PARSE_FAILED;
        }
        if ("LOCAL_FILE".equals(lyric.sourceType)
                && lyric.sourcePath != null
                && !Files.exists(Path.of(lyric.sourcePath))) {
            return LyricStatus.MISSING_FILE;
        }
        return LyricStatus.BOUND;
    }

    private ParsedLyricMetadata parseMetadata(Path path, String content) {
        String title = null;
        String artist = null;
        String album = null;
        for (String line : content.lines().limit(40).toList()) {
            Matcher matcher = LRC_TAG.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            String value = blankToNull(matcher.group(2));
            if ("ti".equals(key)) {
                title = value;
            } else if ("ar".equals(key)) {
                artist = value;
            } else if ("al".equals(key)) {
                album = value;
            }
        }

        ParsedFileName parsedFileName = parseFileName(path.getFileName().toString());
        if (title == null) {
            title = parsedFileName.title();
        }
        if (artist == null) {
            artist = parsedFileName.artist();
        }
        return new ParsedLyricMetadata(title, artist, album);
    }

    private ParsedFileName parseFileName(String fileName) {
        String baseName = stripExtension(fileName).trim();
        String[] parts = baseName.split("\\s+-\\s+", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            return new ParsedFileName(parts[1].trim(), parts[0].trim());
        }
        return new ParsedFileName(baseName, null);
    }

    private Path resolveScanRoot(String requestedPath) {
        String rawPath = requestedPath;
        if (rawPath == null || rawPath.isBlank()) {
            List<String> lyricDirs = config.lyricDirs();
            if (lyricDirs == null || lyricDirs.isEmpty()) {
                throw new BadRequestException("No lyric directories configured");
            }
            rawPath = lyricDirs.getFirst();
        }

        Path requested = Path.of(rawPath.trim());
        rejectPathTraversal(requested);
        Path realPath = toReadableDirectory(requested);
        rejectUnsafeRoot(realPath);
        List<Path> allowedRoots = config.lyricDirs().stream()
                .map(Path::of)
                .peek(this::rejectPathTraversal)
                .map(this::toReadableDirectory)
                .peek(this::rejectUnsafeRoot)
                .toList();
        if (allowedRoots.stream().noneMatch(realPath::startsWith)) {
            throw new BadRequestException("Lyric directory is outside allowed roots: " + requested);
        }
        return realPath;
    }

    private Path toReadableDirectory(Path path) {
        if (!Files.exists(path)) {
            throw new BadRequestException("Lyric directory does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new BadRequestException("Lyric path is not a directory: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new BadRequestException("Lyric directory is not readable: " + path);
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new BadRequestException("Lyric directory cannot be resolved: " + path, exception);
        }
    }

    private void rejectPathTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                throw new BadRequestException("Lyric directory must not contain path traversal: " + path);
            }
        }
    }

    private void rejectUnsafeRoot(Path path) {
        if (UNSAFE_ROOTS.contains(path)) {
            throw new BadRequestException("Lyric directory is not an allowed scan root: " + path);
        }
    }

    private boolean isLrcFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return fileName.endsWith(".lrc");
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
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

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static class ScanCounters {
        int totalFiles;
        int imported;
        int duplicateFiles;
        int matched;
        int unmatched;
        int skippedBindings;
        int failed;

        ScanCounters snapshot() {
            ScanCounters snapshot = new ScanCounters();
            snapshot.copyFrom(this);
            return snapshot;
        }

        void copyFrom(ScanCounters source) {
            totalFiles = source.totalFiles;
            imported = source.imported;
            duplicateFiles = source.duplicateFiles;
            matched = source.matched;
            unmatched = source.unmatched;
            skippedBindings = source.skippedBindings;
            failed = source.failed;
        }
    }

    private record ParsedLyricMetadata(String title, String artist, String album) {
    }

    private record ParsedFileName(String title, String artist) {
    }

    private record MatchResult(Long songId, String matchType, int score) {
    }

    private record LyricQuery(String query, List<Object> parameters, boolean emptyResult) {
    }

    private record BindingSummary(BoundSongResponse boundSong) {
    }

    public record PrimaryLyricSummary(Long lyricId, String lyricStatus) {
    }
}
