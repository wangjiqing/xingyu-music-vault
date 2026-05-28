package com.xingyu.musicvault.openapi;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.artwork.MusicArtworkBindingRepository;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import com.xingyu.musicvault.lyrics.SongLyricRepository;
import com.xingyu.musicvault.openapi.OpenApiDtos.AlbumResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.ArtistResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.ArtworkMetaResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.LyricsMetaResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.LyricsResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.MatchTrackResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.OpenPageResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.OpenTrackResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.ServerInfoResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.SyncChangeItemResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.SyncChangesResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.SyncStateResponse;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/api/open/v1")
@Produces(MediaType.APPLICATION_JSON)
public class OpenApiResource {
    private static final String ACTIVE = "active";
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long DURATION_TOLERANCE_MS = 3_000L;

    @Inject
    SongLyricRepository songLyricRepository;

    @Inject
    MusicArtworkBindingRepository artworkBindingRepository;

    @Inject
    MusicVaultConfig config;

    @Inject
    OpenApiSyncStateService syncStateService;

    @Inject
    OpenApiChangeLogService changeLogService;

    @Inject
    OpenApiHashService hashService;

    @ConfigProperty(name = "app.artwork.scan-dir")
    String artworkScanDir;

    @GET
    @Path("/server/info")
    public ServerInfoResponse serverInfo() {
        return new ServerInfoResponse(
                "xingyu-music-vault",
                "0.9.2",
                "v1",
                true,
                new LinkedHashMap<>(Map.of(
                        "tracks", true,
                        "lyrics", true,
                        "artwork", true,
                        "artists", true,
                        "albums", true,
                        "matchTrack", true,
                        "streaming", false,
                        "writeBack", false,
                        "scanTrigger", false
                ))
        );
    }

    @GET
    @Path("/sync/state")
    public SyncStateResponse syncState() {
        List<TrackFile> trackFiles = activeTrackFiles();
        Map<Long, Track> tracksById = tracksById(trackFiles);
        OpenApiLibraryState syncState = syncStateService.current();
        LocalDateTime lastUpdatedAt = trackFiles.stream()
                .map(trackFile -> max(trackFile.updatedAt, trackOf(trackFile, tracksById) == null ? null : trackOf(trackFile, tracksById).updatedAt))
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return new SyncStateResponse(
                syncState.libraryVersion,
                trackFiles.size(),
                artistAggregates(trackFiles, tracksById).size(),
                albumAggregates(trackFiles, tracksById).size(),
                activeLyricsCount(trackFiles),
                activeArtworkCount(trackFiles),
                lastUpdatedAt,
                syncState.lastChangedAt,
                true
        );
    }

    @GET
    @Path("/sync/changes")
    public SyncChangesResponse syncChanges(
            @QueryParam("sinceVersion") Long sinceVersion,
            @QueryParam("limit") Integer limit
    ) {
        long fromVersion = sinceVersion == null ? 0 : sinceVersion;
        if (fromVersion < 0) {
            throw invalid("sinceVersion must be greater than or equal to 0");
        }
        int limitValue = resolveChangeLimit(limit);
        OpenApiLibraryState state = syncStateService.current();
        List<OpenApiSyncChangeLog> changes = changeLogService.changesAfter(fromVersion, limitValue);
        boolean hasMore = !changes.isEmpty()
                && changeLogService.hasChangesAfter(changes.getLast().version);
        List<SyncChangeItemResponse> items = changes.stream()
                .map(changeLog -> new SyncChangeItemResponse(
                        changeLog.version,
                        changeLog.entityType,
                        changeLog.entityId,
                        changeLog.changeType,
                        changeLogService.changedFields(changeLog),
                        changeLog.changedAt
                ))
                .toList();
        return new SyncChangesResponse(fromVersion, state.libraryVersion, hasMore, items);
    }

    @GET
    @Path("/tracks")
    public OpenPageResponse<OpenTrackResponse> tracks(
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("keyword") String keyword,
            @QueryParam("artist") String artist,
            @QueryParam("album") String album,
            @QueryParam("year") Integer year,
            @QueryParam("genre") String genre,
            @QueryParam("metadataStatus") String metadataStatus,
            @QueryParam("lyricsStatus") String lyricsStatus,
            @QueryParam("artworkStatus") String artworkStatus,
            @QueryParam("updatedAfter") String updatedAfter,
            @QueryParam("sort") String sort,
            @QueryParam("order") String order
    ) {
        int pageValue = resolvePage(page);
        int pageSizeValue = resolvePageSize(pageSize);
        FilterQuery filter = trackFilter(keyword, artist, album, year, genre, metadataStatus, lyricsStatus, artworkStatus, updatedAfter);
        Sort databaseSort = trackFileSort(sort, order);
        PanacheQuery<TrackFile> query = databaseSort == null
                ? TrackFile.find(filter.query(), filter.parameters().toArray())
                : TrackFile.find(filter.query(), databaseSort, filter.parameters().toArray());
        long total = query.count();
        if (databaseSort != null) {
            return new OpenPageResponse<>(
                    toTrackResponses(query.page(Page.of(pageValue, pageSizeValue)).list()),
                    pageValue,
                    pageSizeValue,
                    total
            );
        }
        List<OpenTrackResponse> allItems = toTrackResponses(query.list()).stream()
                .sorted(trackComparator(sort, order))
                .toList();
        int fromIndex = Math.min(pageValue * pageSizeValue, allItems.size());
        int toIndex = Math.min(fromIndex + pageSizeValue, allItems.size());
        return new OpenPageResponse<>(allItems.subList(fromIndex, toIndex), pageValue, pageSizeValue, total);
    }

    @GET
    @Path("/tracks/{id}")
    public OpenTrackResponse track(@PathParam("id") Long id) {
        TrackFile trackFile = findActiveTrackFile(id);
        return toTrackResponse(trackFile, trackOf(trackFile), primaryLyric(trackFile.id), primaryArtwork(trackFile.id));
    }

    @GET
    @Path("/tracks/{id}/lyrics")
    public Response lyrics(
            @PathParam("id") Long id,
            @HeaderParam("If-None-Match") String ifNoneMatch
    ) {
        findActiveTrackFile(id);
        Lyric lyric = findPrimaryLyric(id);
        String hash = hashService.lyricsHash(lyric);
        String etag = hashService.lyricsEtag(id, hash);
        if (hashService.matches(ifNoneMatch, etag)) {
            return Response.status(Response.Status.NOT_MODIFIED)
                    .header("ETag", etag)
                    .build();
        }
        return Response.ok(new LyricsResponse(id, lyric.id, lyric.format, lyric.content, hash, lyric.updatedAt))
                .header("ETag", etag)
                .build();
    }

    @GET
    @Path("/tracks/{id}/lyrics/meta")
    public LyricsMetaResponse lyricsMeta(@PathParam("id") Long id) {
        findActiveTrackFile(id);
        SongLyric binding = songLyricRepository.findPrimaryBySongId(id);
        if (binding == null) {
            return new LyricsMetaResponse(id, false, null, null, null, null, null);
        }
        Lyric lyric = Lyric.findById(binding.lyricId);
        if (lyric == null) {
            return new LyricsMetaResponse(id, false, null, null, null, null, null);
        }
        String hash = hashService.lyricsHash(lyric);
        return new LyricsMetaResponse(id, true, lyric.id, lyric.format, hash, hashService.lyricsEtag(id, hash), lyric.updatedAt);
    }

    @GET
    @Path("/tracks/{id}/artwork")
    @Produces({"image/png", "image/jpeg", "image/webp", MediaType.APPLICATION_OCTET_STREAM})
    public Response artwork(
            @PathParam("id") Long id,
            @HeaderParam("If-None-Match") String ifNoneMatch
    ) {
        findActiveTrackFile(id);
        Artwork artwork = findPrimaryArtwork(id);
        java.nio.file.Path path = safeArtworkPath(artwork);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_ARTWORK_NOT_FOUND", "Artwork not found");
        }
        String hash = hashService.artworkHash(artwork, path);
        String etag = hashService.artworkEtag(id, hash);
        if (hashService.matches(ifNoneMatch, etag)) {
            return Response.status(Response.Status.NOT_MODIFIED)
                    .header("ETag", etag)
                    .build();
        }
        CacheControl cacheControl = new CacheControl();
        cacheControl.setMaxAge(3600);
        return Response.ok(path.toFile(), blankToDefault(artwork.mimeType, MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(cacheControl)
                .header("ETag", etag)
                .build();
    }

    @GET
    @Path("/tracks/{id}/artwork/meta")
    public ArtworkMetaResponse artworkMeta(@PathParam("id") Long id) {
        findActiveTrackFile(id);
        MusicArtworkBinding binding = artworkBindingRepository.findPrimaryTrackCoverByMusicId(id);
        if (binding == null) {
            return new ArtworkMetaResponse(id, false, null, null, null, null, null, null, null, null);
        }
        Artwork artwork = Artwork.findById(binding.artworkId);
        if (artwork == null) {
            return new ArtworkMetaResponse(id, false, null, null, null, null, null, null, null, null);
        }
        java.nio.file.Path path = safeArtworkPath(artwork);
        String hash = hashService.artworkHash(artwork, path);
        return new ArtworkMetaResponse(
                id,
                true,
                artwork.id,
                artwork.mimeType,
                artwork.fileSize,
                artwork.width,
                artwork.height,
                hash,
                hashService.artworkEtag(id, hash),
                artwork.updatedAt
        );
    }

    @GET
    @Path("/artists")
    public List<ArtistResponse> artists() {
        List<TrackFile> trackFiles = activeTrackFiles();
        return artistAggregates(trackFiles, tracksById(trackFiles)).values().stream()
                .map(ArtistAggregate::toResponse)
                .sorted(Comparator.comparing(ArtistResponse::artistName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @GET
    @Path("/artists/{artistName}/tracks")
    public OpenPageResponse<OpenTrackResponse> artistTracks(
            @Encoded @PathParam("artistName") String artistName,
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize
    ) {
        return tracks(page, pageSize, null, decodeRequired(artistName, "artistName"), null, null, null, null, null, null, null, "title", "asc");
    }

    @GET
    @Path("/albums")
    public List<AlbumResponse> albums() {
        List<TrackFile> trackFiles = activeTrackFiles();
        return albumAggregates(trackFiles, tracksById(trackFiles)).values().stream()
                .map(AlbumAggregate::toResponse)
                .sorted(Comparator.comparing(AlbumResponse::album, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(AlbumResponse::albumArtist, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }

    @GET
    @Path("/albums/tracks")
    public OpenPageResponse<OpenTrackResponse> albumTracks(
            @QueryParam("album") String album,
            @QueryParam("artist") String artist,
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize
    ) {
        if (!hasText(album)) {
            throw invalid("album must not be blank");
        }
        return tracks(page, pageSize, null, artist, album, null, null, null, null, null, null, "trackNo", "asc");
    }

    @GET
    @Path("/match/track")
    public MatchTrackResponse matchTrack(
            @QueryParam("title") String title,
            @QueryParam("artist") String artist,
            @QueryParam("album") String album,
            @QueryParam("durationMs") Long durationMs
    ) {
        if (!hasText(title)) {
            throw invalid("title must not be blank");
        }
        String normalizedTitle = normalize(title);
        List<TrackFile> activeTrackFiles = activeTrackFiles();
        Map<Long, Track> tracksById = tracksById(activeTrackFiles);
        List<MatchCandidate> candidates = activeTrackFiles.stream()
                .map(trackFile -> new MatchCandidate(trackFile, trackOf(trackFile, tracksById), 0, ""))
                .filter(candidate -> candidate.track() != null && normalizedTitle.equals(normalize(candidate.track().title)))
                .toList();
        if (candidates.isEmpty()) {
            return new MatchTrackResponse(false, 0, "No exact title match", null);
        }

        MatchCandidate best = candidates.stream()
                .map(candidate -> scoreCandidate(candidate.trackFile(), candidate.track(), artist, album, durationMs))
                .max(Comparator.comparingInt(MatchCandidate::score))
                .orElse(null);
        if (best == null) {
            return new MatchTrackResponse(false, 0, "No exact title match", null);
        }
        return new MatchTrackResponse(
                true,
                best.score(),
                best.reason(),
                toTrackResponse(best.trackFile(), best.track(), primaryLyric(best.trackFile().id), primaryArtwork(best.trackFile().id))
        );
    }

    private List<OpenTrackResponse> toTrackResponses(List<TrackFile> trackFiles) {
        Map<Long, Track> tracksById = tracksById(trackFiles);
        Map<Long, Lyric> lyricsByTrackId = primaryLyrics(trackFiles.stream().map(trackFile -> trackFile.id).toList());
        Map<Long, Artwork> artworksByTrackId = primaryArtworks(trackFiles.stream().map(trackFile -> trackFile.id).toList());
        return trackFiles.stream()
                .map(trackFile -> toTrackResponse(
                        trackFile,
                        trackOf(trackFile, tracksById),
                        lyricsByTrackId.get(trackFile.id),
                        artworksByTrackId.get(trackFile.id)
                ))
                .toList();
    }

    private OpenTrackResponse toTrackResponse(TrackFile trackFile, Track track, Lyric lyric, Artwork artwork) {
        return new OpenTrackResponse(
                trackFile.id,
                titleOf(track, trackFile),
                track == null ? null : track.artist,
                track == null ? null : track.album,
                track == null ? null : track.albumArtist,
                track == null ? null : track.duration,
                track == null ? null : track.year,
                track == null ? null : track.trackNo,
                track == null ? null : track.genre,
                track == null ? null : track.metadataStatus,
                lyric == null ? "NO_LYRIC" : "BOUND",
                artwork == null ? "MISSING" : "BOUND",
                trackFile.fileName,
                trackFile.fileExt,
                trackFile.fileSize,
                lyric != null,
                lyric == null ? null : lyric.id,
                artwork != null,
                artwork == null ? null : artwork.id,
                artwork == null ? null : "/api/open/v1/tracks/" + trackFile.id + "/artwork",
                trackFile.createdAt,
                max(trackFile.updatedAt, track == null ? null : track.updatedAt)
        );
    }

    private FilterQuery trackFilter(
            String keyword,
            String artist,
            String album,
            Integer year,
            String genre,
            String metadataStatus,
            String lyricsStatus,
            String artworkStatus,
            String updatedAfter
    ) {
        StringBuilder query = new StringBuilder("(deleteStatus is null or deleteStatus = ?1)");
        List<Object> params = new ArrayList<>();
        params.add(ACTIVE);
        appendLikeFilter(query, params, keyword);
        appendTrackTextFilter(query, params, "artist", artist);
        appendTrackTextFilter(query, params, "album", album);
        appendTrackEqualsFilter(query, params, "year", year);
        appendTrackTextFilter(query, params, "genre", genre);
        appendTrackTextFilter(query, params, "metadataStatus", metadataStatus);
        appendLyricsStatusFilter(query, params, lyricsStatus);
        appendArtworkStatusFilter(query, params, artworkStatus);
        if (hasText(updatedAfter)) {
            LocalDateTime value = parseDateTime(updatedAfter);
            int idx = params.size() + 1;
            query.append(" and (updatedAt > ?").append(idx)
                    .append(" or trackId in (select t.id from Track t where t.updatedAt > ?").append(idx)
                    .append("))");
            params.add(value);
        }
        return new FilterQuery(query.toString(), params);
    }

    private void appendLikeFilter(StringBuilder query, List<Object> params, String keyword) {
        if (!hasText(keyword)) {
            return;
        }
        int idx = params.size() + 1;
        query.append(" and (lower(fileName) like ?").append(idx)
                .append(" or trackId in (select t.id from Track t where lower(t.title) like ?").append(idx)
                .append(" or lower(t.artist) like ?").append(idx)
                .append(" or lower(t.album) like ?").append(idx)
                .append(" or lower(t.genre) like ?").append(idx)
                .append("))");
        params.add("%" + keyword.trim().toLowerCase(Locale.ROOT) + "%");
    }

    private void appendTrackTextFilter(StringBuilder query, List<Object> params, String field, String value) {
        if (!hasText(value)) {
            return;
        }
        int idx = params.size() + 1;
        query.append(" and trackId in (select t.id from Track t where lower(trim(t.")
                .append(field)
                .append(")) = ?")
                .append(idx)
                .append(")");
        params.add(value.trim().toLowerCase(Locale.ROOT));
    }

    private void appendTrackEqualsFilter(StringBuilder query, List<Object> params, String field, Object value) {
        if (value == null) {
            return;
        }
        int idx = params.size() + 1;
        query.append(" and trackId in (select t.id from Track t where t.")
                .append(field)
                .append(" = ?")
                .append(idx)
                .append(")");
        params.add(value);
    }

    private void appendLyricsStatusFilter(StringBuilder query, List<Object> params, String value) {
        if (!hasText(value)) {
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("BOUND".equals(normalized) || "AVAILABLE".equals(normalized)) {
            query.append(" and id in (select sl.songId from SongLyric sl where sl.isPrimary = true)");
            return;
        }
        if ("NO_LYRIC".equals(normalized) || "MISSING".equals(normalized)) {
            query.append(" and id not in (select sl.songId from SongLyric sl where sl.isPrimary = true)");
            return;
        }
        appendTrackTextFilter(query, params, "lyricsStatus", value);
    }

    private void appendArtworkStatusFilter(StringBuilder query, List<Object> params, String value) {
        if (!hasText(value)) {
            return;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if ("BOUND".equals(normalized) || "AVAILABLE".equals(normalized)) {
            query.append(" and id in (select mab.musicId from MusicArtworkBinding mab where mab.isPrimary = true and mab.relationType = '")
                    .append(ArtworkService.TRACK_COVER)
                    .append("')");
            return;
        }
        if ("MISSING".equals(normalized)) {
            query.append(" and id not in (select mab.musicId from MusicArtworkBinding mab where mab.isPrimary = true and mab.relationType = '")
                    .append(ArtworkService.TRACK_COVER)
                    .append("')");
            return;
        }
        appendTrackTextFilter(query, params, "artworkStatus", value);
    }

    private Comparator<OpenTrackResponse> trackComparator(String sort, String order) {
        String sortValue = hasText(sort) ? sort.trim() : "updatedAt";
        Comparator<OpenTrackResponse> comparator = switch (sortValue) {
            case "title" -> Comparator.comparing(OpenTrackResponse::title, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "artist" -> Comparator.comparing(OpenTrackResponse::artist, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "album" -> Comparator.comparing(OpenTrackResponse::album, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "year" -> Comparator.comparing(OpenTrackResponse::year, Comparator.nullsLast(Comparator.naturalOrder()));
            case "durationMs" -> Comparator.comparing(OpenTrackResponse::durationMs, Comparator.nullsLast(Comparator.naturalOrder()));
            case "trackNo" -> Comparator.comparing(OpenTrackResponse::trackNo, Comparator.nullsLast(Comparator.naturalOrder()));
            case "metadataStatus" -> Comparator.comparing(OpenTrackResponse::metadataStatus, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "lyricsStatus" -> Comparator.comparing(OpenTrackResponse::lyricsStatus, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "artworkStatus" -> Comparator.comparing(OpenTrackResponse::artworkStatus, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "fileName" -> Comparator.comparing(OpenTrackResponse::fileName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "createdAt" -> Comparator.comparing(OpenTrackResponse::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case "updatedAt" -> Comparator.comparing(OpenTrackResponse::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> throw new OpenApiException(Response.Status.BAD_REQUEST, "OPENAPI_UNSUPPORTED_SORT", "sort is not supported");
        };
        boolean ascending = "asc".equalsIgnoreCase(blankToDefault(order, "desc"));
        Comparator<OpenTrackResponse> withTieBreaker = comparator.thenComparing(OpenTrackResponse::id);
        return ascending ? withTieBreaker : withTieBreaker.reversed();
    }

    private Sort trackFileSort(String sort, String order) {
        String sortValue = hasText(sort) ? sort.trim() : "updatedAt";
        String property = switch (sortValue) {
            case "fileName" -> "fileName";
            case "createdAt" -> "createdAt";
            case "updatedAt" -> "updatedAt";
            default -> null;
        };
        if (property == null) {
            return null;
        }
        boolean ascending = "asc".equalsIgnoreCase(blankToDefault(order, "desc"));
        return ascending ? Sort.ascending(property, "id") : Sort.descending(property, "id");
    }

    private MatchCandidate scoreCandidate(TrackFile trackFile, Track track, String artist, String album, Long durationMs) {
        int score = 70;
        List<String> reasons = new ArrayList<>();
        reasons.add("title exact match");
        if (track != null && hasText(artist) && normalize(artist).equals(normalize(track.artist))) {
            score += 15;
            reasons.add("artist exact match");
        }
        if (track != null && hasText(album) && normalize(album).equals(normalize(track.album))) {
            score += 10;
            reasons.add("album exact match");
        }
        if (track != null && durationMs != null && track.duration != null) {
            long diff = Math.abs(track.duration - durationMs);
            if (diff <= DURATION_TOLERANCE_MS) {
                score += 5;
                reasons.add("duration within +/-3000ms");
            } else {
                reasons.add("duration differs by " + diff + "ms");
            }
        }
        return new MatchCandidate(trackFile, track, Math.min(score, 100), String.join("; ", reasons));
    }

    private java.nio.file.Path safeArtworkPath(Artwork artwork) {
        java.nio.file.Path path = java.nio.file.Path.of(artwork.filePath).toAbsolutePath().normalize();
        java.nio.file.Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (java.io.IOException exception) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_ARTWORK_NOT_FOUND", "Artwork not found");
        }
        if (allowedArtworkRoots().stream().noneMatch(root -> realPath.startsWith(root) && !realPath.equals(root))) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_ARTWORK_NOT_FOUND", "Artwork not found");
        }
        return realPath;
    }

    private List<java.nio.file.Path> allowedArtworkRoots() {
        List<java.nio.file.Path> roots = new ArrayList<>();
        if (hasText(artworkScanDir)) {
            roots.add(java.nio.file.Path.of(artworkScanDir));
        }
        if (config.musicDirs() != null) {
            config.musicDirs().stream()
                    .filter(OpenApiResource::hasText)
                    .map(java.nio.file.Path::of)
                    .forEach(roots::add);
        }
        return roots.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .map(this::existingRealPath)
                .filter(Objects::nonNull)
                .toList();
    }

    private java.nio.file.Path existingRealPath(java.nio.file.Path path) {
        try {
            return path.toRealPath();
        } catch (java.io.IOException exception) {
            return null;
        }
    }

    private TrackFile findActiveTrackFile(Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null || !(trackFile.deleteStatus == null || ACTIVE.equals(trackFile.deleteStatus))) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_TRACK_NOT_FOUND", "Track not found");
        }
        return trackFile;
    }

    private Lyric findPrimaryLyric(Long trackId) {
        SongLyric binding = songLyricRepository.findPrimaryBySongId(trackId);
        if (binding == null) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_LYRICS_NOT_FOUND", "Lyrics not found");
        }
        Lyric lyric = Lyric.findById(binding.lyricId);
        if (lyric == null) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_LYRICS_NOT_FOUND", "Lyrics not found");
        }
        return lyric;
    }

    private Artwork findPrimaryArtwork(Long trackId) {
        MusicArtworkBinding binding = artworkBindingRepository.findPrimaryTrackCoverByMusicId(trackId);
        if (binding == null) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_ARTWORK_NOT_FOUND", "Artwork not found");
        }
        Artwork artwork = Artwork.findById(binding.artworkId);
        if (artwork == null) {
            throw new OpenApiException(Response.Status.NOT_FOUND, "OPENAPI_ARTWORK_NOT_FOUND", "Artwork not found");
        }
        return artwork;
    }

    private Lyric primaryLyric(Long trackId) {
        SongLyric binding = songLyricRepository.findPrimaryBySongId(trackId);
        return binding == null ? null : Lyric.findById(binding.lyricId);
    }

    private Artwork primaryArtwork(Long trackId) {
        MusicArtworkBinding binding = artworkBindingRepository.findPrimaryTrackCoverByMusicId(trackId);
        return binding == null ? null : Artwork.findById(binding.artworkId);
    }

    private Map<Long, Lyric> primaryLyrics(List<Long> trackIds) {
        List<SongLyric> bindings = songLyricRepository.findPrimaryBySongIds(trackIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }
        Map<Long, Lyric> lyrics = Lyric.<Lyric>list(
                "id in ?1",
                bindings.stream().map(binding -> binding.lyricId).distinct().toList()
        ).stream().collect(Collectors.toMap(lyric -> lyric.id, Function.identity()));
        return bindings.stream()
                .filter(binding -> lyrics.containsKey(binding.lyricId))
                .collect(Collectors.toMap(binding -> binding.songId, binding -> lyrics.get(binding.lyricId), (left, right) -> left));
    }

    private Map<Long, Artwork> primaryArtworks(List<Long> trackIds) {
        List<MusicArtworkBinding> bindings = artworkBindingRepository.findPrimaryTrackCoversByMusicIds(trackIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }
        Map<Long, Artwork> artworks = Artwork.<Artwork>list(
                "id in ?1",
                bindings.stream().map(binding -> binding.artworkId).distinct().toList()
        ).stream().collect(Collectors.toMap(artwork -> artwork.id, Function.identity()));
        return bindings.stream()
                .filter(binding -> artworks.containsKey(binding.artworkId))
                .collect(Collectors.toMap(binding -> binding.musicId, binding -> artworks.get(binding.artworkId), (left, right) -> left));
    }

    private List<TrackFile> activeTrackFiles() {
        return TrackFile.list("deleteStatus is null or deleteStatus = ?1", ACTIVE);
    }

    private long activeLyricsCount(List<TrackFile> trackFiles) {
        List<Long> trackIds = trackFiles.stream().map(trackFile -> trackFile.id).toList();
        if (trackIds.isEmpty()) {
            return 0;
        }
        return SongLyric.count("songId in ?1 and isPrimary = true", trackIds);
    }

    private long activeArtworkCount(List<TrackFile> trackFiles) {
        List<Long> trackIds = trackFiles.stream().map(trackFile -> trackFile.id).toList();
        if (trackIds.isEmpty()) {
            return 0;
        }
        return MusicArtworkBinding.count(
                "musicId in ?1 and relationType = ?2 and isPrimary = true",
                trackIds,
                ArtworkService.TRACK_COVER
        );
    }

    private Map<Long, Track> tracksById(List<TrackFile> trackFiles) {
        List<Long> trackIds = trackFiles.stream().map(trackFile -> trackFile.trackId).filter(Objects::nonNull).distinct().toList();
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        return Track.<Track>list("id in ?1", trackIds).stream()
                .collect(Collectors.toMap(track -> track.id, Function.identity()));
    }

    private Track trackOf(TrackFile trackFile) {
        return trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
    }

    private Track trackOf(TrackFile trackFile, Map<Long, Track> tracksById) {
        return trackFile.trackId == null ? null : tracksById.get(trackFile.trackId);
    }

    private Map<String, ArtistAggregate> artistAggregates(List<TrackFile> trackFiles, Map<Long, Track> tracksById) {
        Map<Long, Lyric> lyrics = primaryLyrics(trackFiles.stream().map(trackFile -> trackFile.id).toList());
        Map<Long, Artwork> artworks = primaryArtworks(trackFiles.stream().map(trackFile -> trackFile.id).toList());
        Map<String, ArtistAggregate> result = new LinkedHashMap<>();
        for (TrackFile trackFile : trackFiles) {
            Track track = trackOf(trackFile, tracksById);
            String artistName = blankToDefault(track == null ? null : track.artist, "Unknown");
            ArtistAggregate aggregate = result.computeIfAbsent(normalize(artistName), ignored -> new ArtistAggregate(artistName));
            aggregate.add(track, lyrics.containsKey(trackFile.id), artworks.containsKey(trackFile.id));
        }
        return result;
    }

    private Map<String, AlbumAggregate> albumAggregates(List<TrackFile> trackFiles, Map<Long, Track> tracksById) {
        Map<Long, Lyric> lyrics = primaryLyrics(trackFiles.stream().map(trackFile -> trackFile.id).toList());
        Map<Long, Artwork> artworks = primaryArtworks(trackFiles.stream().map(trackFile -> trackFile.id).toList());
        Map<String, AlbumAggregate> result = new LinkedHashMap<>();
        for (TrackFile trackFile : trackFiles) {
            Track track = trackOf(trackFile, tracksById);
            String album = blankToDefault(track == null ? null : track.album, "Unknown");
            String albumArtist = blankToDefault(track == null ? null : track.albumArtist, track == null ? null : track.artist);
            String key = normalize(album) + "|" + normalize(albumArtist);
            AlbumAggregate aggregate = result.computeIfAbsent(key, ignored -> new AlbumAggregate(album, albumArtist));
            aggregate.add(track, lyrics.containsKey(trackFile.id), artworks.containsKey(trackFile.id));
        }
        return result;
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw invalid("page must be greater than or equal to 0");
        }
        return page;
    }

    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw invalid("pageSize must be between 1 and 100");
        }
        return pageSize;
    }

    private int resolveChangeLimit(Integer limit) {
        if (limit == null) {
            return 500;
        }
        if (limit < 1 || limit > 1000) {
            throw invalid("limit must be between 1 and 1000");
        }
        return limit;
    }

    private OpenApiException invalid(String message) {
        return new OpenApiException(Response.Status.BAD_REQUEST, "OPENAPI_INVALID_ARGUMENT", message);
    }

    private LocalDateTime parseDateTime(String value) {
        try {
            return LocalDateTime.parse(value.trim());
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(value.trim()).toLocalDateTime();
            } catch (DateTimeParseException exception) {
                throw invalid("updatedAfter must be an ISO-8601 date-time");
            }
        }
    }

    private String decodeRequired(String value, String label) {
        if (!hasText(value)) {
            throw invalid(label + " must not be blank");
        }
        return URLDecoder.decode(value.trim(), StandardCharsets.UTF_8);
    }

    private static String titleOf(Track track, TrackFile trackFile) {
        if (track != null && hasText(track.title)) {
            return track.title;
        }
        int dotIndex = trackFile.fileName.lastIndexOf('.');
        return dotIndex <= 0 ? trackFile.fileName : trackFile.fileName.substring(0, dotIndex);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String blankToDefault(String value, String defaultValue) {
        return hasText(value) ? value.trim() : defaultValue;
    }

    private static LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private record FilterQuery(String query, List<Object> parameters) {
    }

    private record MatchCandidate(TrackFile trackFile, Track track, int score, String reason) {
    }

    private static final class ArtistAggregate {
        private final String artistName;
        private final Set<String> albums = new java.util.HashSet<>();
        private long trackCount;
        private long lyricsCount;
        private long artworkCount;

        private ArtistAggregate(String artistName) {
            this.artistName = artistName;
        }

        private void add(Track track, boolean hasLyrics, boolean hasArtwork) {
            trackCount++;
            albums.add(normalize(track == null ? null : track.album));
            if (hasLyrics) {
                lyricsCount++;
            }
            if (hasArtwork) {
                artworkCount++;
            }
        }

        private ArtistResponse toResponse() {
            return new ArtistResponse(artistName, trackCount, albums.size(), lyricsCount, artworkCount);
        }
    }

    private static final class AlbumAggregate {
        private final String album;
        private final String albumArtist;
        private Integer year;
        private long trackCount;
        private long lyricsCount;
        private long artworkCount;

        private AlbumAggregate(String album, String albumArtist) {
            this.album = album;
            this.albumArtist = albumArtist;
        }

        private void add(Track track, boolean hasLyrics, boolean hasArtwork) {
            trackCount++;
            if (year == null && track != null) {
                year = track.year;
            }
            if (hasLyrics) {
                lyricsCount++;
            }
            if (hasArtwork) {
                artworkCount++;
            }
        }

        private AlbumResponse toResponse() {
            return new AlbumResponse(album, albumArtist, year, trackCount, lyricsCount, artworkCount);
        }
    }
}
