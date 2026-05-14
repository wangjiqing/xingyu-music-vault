package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.LyricDtos.LyricScanResponse;
import com.xingyu.musicvault.lyrics.LyricDtos.SongLyricResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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

    @Transactional
    public LyricScanResponse scan(String requestedPath, boolean overwritePrimary) {
        Path root = resolveScanRoot(requestedPath);
        ScanCounters counters = new ScanCounters();
        LOG.infof("Scanning lyric directory: root=%s overwritePrimary=%s", root, overwritePrimary);

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isLrcFile)
                    .forEach(path -> scanFile(path, overwritePrimary, counters));
        } catch (IOException | UncheckedIOException exception) {
            throw new BadRequestException("Failed to scan lyric directory: " + exception.getMessage(), exception);
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

    private void scanFile(Path path, boolean overwritePrimary, ScanCounters counters) {
        counters.totalFiles++;
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            String contentHash = sha256(content);
            Lyric lyric = lyricRepository.findByContentHash(contentHash);
            boolean duplicate = lyric != null;
            ParsedLyricMetadata metadata = parseMetadata(path, content);

            if (duplicate) {
                counters.duplicateFiles++;
            } else {
                lyric = new Lyric();
                lyric.title = metadata.title();
                lyric.artist = metadata.artist();
                lyric.album = metadata.album();
                lyric.sourceType = "LOCAL_FILE";
                lyric.sourcePath = path.toAbsolutePath().normalize().toString();
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
                counters.matched++;
            } else {
                counters.skippedBindings++;
            }
        } catch (Exception exception) {
            counters.failed++;
            LOG.warnf(exception, "Failed to import lyric file: path=%s", path);
        }
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
    }

    private record ParsedLyricMetadata(String title, String artist, String album) {
    }

    private record ParsedFileName(String title, String artist) {
    }

    private record MatchResult(Long songId, String matchType, int score) {
    }

    public record PrimaryLyricSummary(Long lyricId, String lyricStatus) {
    }
}
