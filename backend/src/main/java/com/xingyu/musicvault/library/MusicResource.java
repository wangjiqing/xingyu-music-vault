package com.xingyu.musicvault.library;

import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkBindRequest;
import com.xingyu.musicvault.artwork.ArtworkDtos.MusicArtworkResponse;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.MusicDtos.MusicFileResponse;
import com.xingyu.musicvault.library.MusicDtos.AlbumPageResponse;
import com.xingyu.musicvault.library.MusicDtos.AlbumResponse;
import com.xingyu.musicvault.library.MusicDtos.ArtistAlbumResponse;
import com.xingyu.musicvault.library.MusicDtos.ArtistDetailResponse;
import com.xingyu.musicvault.library.MusicDtos.ArtistPageResponse;
import com.xingyu.musicvault.library.MusicDtos.ArtistResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicMetadataBatchUpdateRequest;
import com.xingyu.musicvault.library.MusicDtos.MusicMetadataBatchUpdateResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicMetadataUpdateRequest;
import com.xingyu.musicvault.library.MusicDtos.MusicResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicScanAccepted;
import com.xingyu.musicvault.library.MusicDtos.MusicScanRequest;
import com.xingyu.musicvault.library.MusicDtos.MusicStatsResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicTrashResponse;
import com.xingyu.musicvault.lyrics.LyricService;
import com.xingyu.musicvault.scan.LibraryScanService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.Encoded;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/api/music")
@Produces(MediaType.APPLICATION_JSON)
public class MusicResource {
    private static final Logger LOG = Logger.getLogger(MusicResource.class);
    private static final String DELETE_STATUS_ACTIVE = "active";
    private static final String DELETE_STATUS_TRASHED = "trashed";
    private static final String DELETE_STATUS_DELETED = "deleted";
    private static final String TRASH_DIRECTORY_NAME = ".music-vault-trash";
    private static final int MAX_BATCH_METADATA_UPDATE_IDS = 500;
    private static final String UNKNOWN_ARTIST = "未知歌手";
    private static final String UNKNOWN_ARTIST_KEY = "__unknown__";
    private static final String UNKNOWN_ALBUM = "未知专辑";
    private static final String UNKNOWN_ALBUM_KEY = "__unknown__";

    @Inject
    MusicVaultConfig config;

    @ConfigProperty(name = "music.scan.default-path")
    String defaultScanPath;

    @Inject
    LibraryScanService libraryScanService;

    @Inject
    LyricService lyricService;

    @Inject
    ArtworkService artworkService;

    @Inject
    ManagedExecutor managedExecutor;

    @POST
    @Path("/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response scan(MusicScanRequest request) {
        String scanPath = resolveScanPath(request);
        ScanJob scanJob = createScanJob(scanPath);
        managedExecutor.execute(() -> runScanJob(scanJob.id));
        return Response.accepted(MusicScanAccepted.from(scanJob)).build();
    }

    private ScanJob createScanJob(String scanPath) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ScanJob scanJob = new ScanJob();
            scanJob.jobType = "library_scan";
            scanJob.status = "pending";
            scanJob.musicDirs = scanPath;
            scanJob.persist();
            LOG.infof("Accepted direct music scan job: id=%d path=%s", scanJob.id, scanPath);
            return scanJob;
        });
    }

    private void runScanJob(Long scanJobId) {
        try {
            libraryScanService.run(scanJobId);
        } catch (Exception exception) {
            LOG.errorf(exception, "Failed to execute accepted music scan job: id=%d", scanJobId);
        }
    }

    @GET
    public PageResponse<MusicResponse> list(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("keyword") String keyword,
            @QueryParam("artistKey") String artistKey,
            @QueryParam("albumKey") String albumKey,
            @QueryParam("year") Integer year,
            @QueryParam("genre") String genre,
            @QueryParam("metadata") String metadata,
            @QueryParam("hasLyrics") Boolean hasLyrics,
            @QueryParam("hasArtwork") Boolean hasArtwork
    ) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size == null ? pageSize : size);

        FilterQuery filter = buildFilterQuery(keyword, artistKey, albumKey, year, genre, metadata, hasLyrics, hasArtwork);
        PanacheQuery<TrackFile> query = TrackFile.find(
                filter.query(),
                Sort.descending("createdAt"),
                filter.parameters().toArray()
        );
        long total = query.count();
        List<TrackFile> trackFiles = query.page(Page.of(pageValue, sizeValue)).list();
        Map<Long, Track> tracksById = tracksById(trackFiles);
        Map<Long, LyricService.PrimaryLyricSummary> lyricsBySongId = lyricService.primaryLyricsForSongIds(
                trackFiles.stream().map(trackFile -> trackFile.id).toList()
        );
        Map<Long, ArtworkService.PrimaryArtworkSummary> artworksByMusicId = artworkService.primaryArtworkForMusicIds(
                trackFiles.stream().map(trackFile -> trackFile.id).toList()
        );
        List<MusicResponse> items = trackFiles.stream()
                .map(trackFile -> toMusicResponse(
                        trackFile,
                        trackOf(trackFile, tracksById),
                        lyricsBySongId.get(trackFile.id),
                        artworksByMusicId.get(trackFile.id)
                ))
                .toList();
        return new PageResponse<>(items, pageValue, sizeValue, total);
    }

    @GET
    @Path("/stats")
    public MusicStatsResponse stats() {
        long total = TrackFile.count(
                "deleteStatus is null or deleteStatus = ?1",
                DELETE_STATUS_ACTIVE
        );
        long trashed = TrackFile.count("deleteStatus", DELETE_STATUS_TRASHED);
        long metadataIncomplete = TrackFile.count(
                "(deleteStatus is null or deleteStatus = ?1) and (trackId is null or trackId in (select t.id from Track t where t.title is null or t.artist is null or t.album is null))",
                DELETE_STATUS_ACTIVE
        );
        long lyricsReady = TrackFile.count(
                "(deleteStatus is null or deleteStatus = ?1) and id in (select sl.songId from SongLyric sl where sl.isPrimary = true)",
                DELETE_STATUS_ACTIVE
        );
        long artworkReady = TrackFile.count(
                "(deleteStatus is null or deleteStatus = ?1) and id in (select mab.musicId from MusicArtworkBinding mab where mab.isPrimary = true and mab.relationType = ?2)",
                DELETE_STATUS_ACTIVE,
                ArtworkService.TRACK_COVER
        );
        return new MusicStatsResponse(total, metadataIncomplete, lyricsReady, artworkReady, trashed);
    }

    @GET
    @Path("/trash")
    public List<MusicTrashResponse> trash() {
        List<TrackFile> trackFiles = TrackFile.list(
                "deleteStatus",
                Sort.descending("deletedAt"),
                DELETE_STATUS_TRASHED
        );
        Map<Long, Track> tracksById = tracksById(trackFiles);
        return trackFiles.stream()
                .map(trackFile -> MusicTrashResponse.from(trackFile, trackOf(trackFile, tracksById)))
                .toList();
    }

    @GET
    @Path("/artists")
    public ArtistPageResponse artists(
            @QueryParam("keyword") String keyword,
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("sort") String sort
    ) {
        int pageValue = resolveArtistPage(page);
        int pageSizeValue = resolveArtistPageSize(pageSize);
        String sortValue = resolveArtistSort(sort);

        Map<String, ArtistAggregate> aggregates = artistAggregates(activeTrackFiles());

        String keywordValue = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<ArtistResponse> allItems = aggregates.values().stream()
                .filter(aggregate -> keywordValue == null || keywordValue.isBlank()
                        || aggregate.artist.toLowerCase(Locale.ROOT).contains(keywordValue))
                .map(ArtistAggregate::toResponse)
                .sorted(artistComparator(sortValue))
                .toList();

        int fromIndex = Math.min((pageValue - 1) * pageSizeValue, allItems.size());
        int toIndex = Math.min(fromIndex + pageSizeValue, allItems.size());
        return new ArtistPageResponse(allItems.subList(fromIndex, toIndex), allItems.size(), pageValue, pageSizeValue);
    }

    @GET
    @Path("/albums")
    public AlbumPageResponse albums(
            @QueryParam("keyword") String keyword,
            @QueryParam("artistKey") String artistKey,
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize,
            @QueryParam("sort") String sort
    ) {
        int pageValue = resolveArtistPage(page);
        int pageSizeValue = resolveArtistPageSize(pageSize);
        String sortValue = resolveAlbumSort(sort);
        String requestedArtistKey = artistKey == null || artistKey.isBlank() ? null : canonicalArtistKey(artistKey);

        String keywordValue = keyword == null ? null : keyword.trim().toLowerCase(Locale.ROOT);
        List<AlbumResponse> allItems = albumAggregates(activeTrackFiles()).values().stream()
                .filter(aggregate -> requestedArtistKey == null || aggregate.artistKey.equals(requestedArtistKey))
                .filter(aggregate -> keywordValue == null || keywordValue.isBlank()
                        || aggregate.album.toLowerCase(Locale.ROOT).contains(keywordValue)
                        || aggregate.albumArtist.toLowerCase(Locale.ROOT).contains(keywordValue))
                .map(AlbumAggregate::toAlbumResponse)
                .sorted(albumComparator(sortValue))
                .toList();

        int fromIndex = Math.min((pageValue - 1) * pageSizeValue, allItems.size());
        int toIndex = Math.min(fromIndex + pageSizeValue, allItems.size());
        return new AlbumPageResponse(allItems.subList(fromIndex, toIndex), allItems.size(), pageValue, pageSizeValue);
    }

    @GET
    @Path("/albums/detail")
    public AlbumResponse albumDetail(
            @QueryParam("albumKey") String albumKey,
            @QueryParam("artistKey") String artistKey
    ) {
        String requestedAlbumKey = canonicalAlbumKey(albumKey);
        String requestedArtistKey = canonicalArtistKey(artistKey);
        AlbumAggregate aggregate = albumAggregates(activeTrackFiles()).get(albumAggregateKey(requestedAlbumKey, requestedArtistKey));
        if (aggregate == null) {
            throw new NotFoundException("Album not found");
        }
        return aggregate.toAlbumResponse();
    }

    @GET
    @Path("/artists/{artistKey:.+}")
    public ArtistDetailResponse artistDetail(@Encoded @PathParam("artistKey") String artistKey) {
        String requestedArtistKey = canonicalArtistKey(artistKey);
        ArtistAggregate aggregate = artistAggregates(activeTrackFiles()).get(requestedArtistKey);
        if (aggregate == null) {
            throw new NotFoundException("Artist not found");
        }
        return aggregate.toDetailResponse();
    }

    @GET
    @Path("/{id}")
    public MusicResponse get(@PathParam("id") Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        // Trashed and deleted records are intentionally reachable by ID
        // so callers can inspect file-level details (trashPath etc.).
        return toMusicResponse(trackFile);
    }

    @GET
    @Path("/{id}/file")
    public MusicFileResponse getFile(@PathParam("id") Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        return MusicFileResponse.from(trackFile);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public MusicFileResponse safeDelete(@PathParam("id") Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        if (DELETE_STATUS_TRASHED.equals(trackFile.deleteStatus)) {
            throw new ConflictException("Music is already trashed");
        }

        DeletableMusicFile musicFile = resolveDeletableMusicFile(trackFile.filePath);
        java.nio.file.Path sourcePath = musicFile.sourcePath();
        java.nio.file.Path trashPath = uniqueTrashPath(
                musicFile.libraryRoot(),
                trackFile.id,
                sourcePath.getFileName().toString()
        );
        try {
            Files.createDirectories(trashPath.getParent());
        } catch (IOException exception) {
            throw new ConflictException("Failed to create trash directory");
        }

        trackFile.deletedAt = LocalDateTime.now();
        trackFile.trashPath = trashPath.toAbsolutePath().normalize().toString();
        trackFile.originalPath = trackFile.filePath;
        trackFile.deleteStatus = DELETE_STATUS_TRASHED;
        // Flush to catch DB constraint violations before the filesystem move.
        // JTA rollback on exception protects the normal error path;
        // an unlikely JTA commit failure after the file move would leave
        // the file in trash without a matching DB update (acceptable for
        // a local single-user music library).
        trackFile.persistAndFlush();

        try {
            moveToTrash(sourcePath, trashPath);
        } catch (IOException exception) {
            throw new ConflictException("Failed to move music file to trash");
        }

        return MusicFileResponse.from(trackFile);
    }

    @POST
    @Path("/{id}/restore")
    @Transactional
    public MusicFileResponse restore(@PathParam("id") Long id) {
        TrackFile trackFile = findMusic(id);
        requireTrashed(trackFile);

        TrashMusicFile trashFile = resolveTrashMusicFile(trackFile);
        java.nio.file.Path originalPath = resolveRestoreTarget(trackFile, trashFile.libraryRoot());
        if (Files.exists(originalPath)) {
            throw new ConflictException("Original music file path already exists");
        }
        // Validate the deepest existing ancestor before creating any
        // directories, so symlink escapes are caught early.
        verifyExistingAncestorInsideLibrary(originalPath, trashFile.libraryRoot());

        try {
            Files.createDirectories(originalPath.getParent());
            moveFromTrash(trashFile.trashPath(), originalPath);
        } catch (IOException exception) {
            throw new ConflictException("Failed to restore music file");
        }

        trackFile.filePath = originalPath.toAbsolutePath().normalize().toString();
        trackFile.originalPath = trackFile.filePath;
        trackFile.deletedAt = null;
        trackFile.trashPath = null;
        trackFile.deleteStatus = DELETE_STATUS_ACTIVE;
        try {
            trackFile.persistAndFlush();
        } catch (RuntimeException exception) {
            try {
                moveFromTrash(originalPath, trashFile.trashPath());
            } catch (IOException rollbackException) {
                LOG.warnf(
                        rollbackException,
                        "Failed to move restored file back to trash after database update failure: musicId=%d",
                        trackFile.id
                );
            }
            throw exception;
        }
        return MusicFileResponse.from(trackFile);
    }

    @DELETE
    @Path("/{id}/trash")
    @Transactional
    public MusicFileResponse permanentlyDeleteTrashFile(@PathParam("id") Long id) {
        TrackFile trackFile = findMusic(id);
        requireTrashed(trackFile);

        TrashMusicFile trashFile = resolveTrashMusicFile(trackFile);
        if (trackFile.originalPath == null || trackFile.originalPath.isBlank()) {
            trackFile.originalPath = trackFile.filePath;
        }
        trackFile.deleteStatus = DELETE_STATUS_DELETED;
        if (trackFile.deletedAt == null) {
            trackFile.deletedAt = LocalDateTime.now();
        }
        trackFile.persistAndFlush();

        try {
            Files.delete(trashFile.trashPath());
        } catch (IOException exception) {
            throw new ConflictException("Failed to permanently delete trash file");
        }

        return MusicFileResponse.from(trackFile);
    }

    @PUT
    @Path("/metadata/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public MusicMetadataBatchUpdateResponse batchUpdateMetadata(MusicMetadataBatchUpdateRequest request) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (request.ids() == null || request.ids().isEmpty()) {
            throw new BadRequestException("ids must not be empty");
        }
        if (request.ids().stream().anyMatch(Objects::isNull)) {
            throw new BadRequestException("ids must not contain null");
        }
        if (request.ids().size() > MAX_BATCH_METADATA_UPDATE_IDS) {
            throw new BadRequestException("ids size must be less than or equal to " + MAX_BATCH_METADATA_UPDATE_IDS);
        }

        List<Long> ids = request.ids().stream().distinct().toList();
        String artist = cleanText(request.artist());
        String album = cleanText(request.album());
        String genre = cleanText(request.genre());
        boolean hasArtist = artist != null;
        boolean hasAlbum = album != null;
        boolean hasYear = request.year() != null;
        boolean hasGenre = genre != null;
        if (!hasArtist && !hasAlbum && !hasYear && !hasGenre) {
            throw new BadRequestException("At least one updatable metadata field is required");
        }
        validateYear(request.year());

        List<TrackFile> trackFiles = TrackFile.list("id in ?1", ids);
        Set<Long> foundIds = trackFiles.stream()
                .map(trackFile -> trackFile.id)
                .collect(Collectors.toSet());
        List<Long> missingIds = ids.stream()
                .filter(id -> !foundIds.contains(id))
                .toList();
        if (!missingIds.isEmpty()) {
            throw new NotFoundException("Music not found: " + missingIds);
        }

        List<Long> inactiveIds = trackFiles.stream()
                .filter(trackFile -> !isActive(trackFile))
                .map(trackFile -> trackFile.id)
                .toList();
        if (!inactiveIds.isEmpty()) {
            throw new ConflictException("Only active music can be updated: " + inactiveIds);
        }

        Map<Long, Track> tracksById = tracksById(trackFiles);
        LocalDateTime updatedAt = LocalDateTime.now();
        for (TrackFile trackFile : trackFiles) {
            Track track = trackOf(trackFile, tracksById);
            if (track == null) {
                track = new Track();
            }
            if (hasArtist) {
                track.artist = artist;
            }
            if (hasAlbum) {
                track.album = album;
            }
            if (hasYear) {
                track.year = request.year();
            }
            if (hasGenre) {
                track.genre = genre;
            }
            track.metadataUpdatedAt = updatedAt;

            if (!track.isPersistent()) {
                track.persist();
                trackFile.trackId = track.id;
            }
        }

        return new MusicMetadataBatchUpdateResponse(trackFiles.size());
    }

    @PUT
    @Path("/{id}/metadata")
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public MusicResponse updateMetadata(
            @PathParam("id") Long id,
            MusicMetadataUpdateRequest request
    ) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }

        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }

        validateYear(request.year());
        validateTrackNo(request.trackNo());

        Track track = trackOf(trackFile);
        String title = cleanText(request.title());
        track.title = title;
        track.normalizedTitle = title == null ? null : title.toLowerCase(Locale.ROOT);
        track.artist = cleanText(request.artist());
        track.album = cleanText(request.album());
        track.year = request.year();
        track.trackNo = request.trackNo();
        track.genre = cleanText(request.genre());
        track.metadataUpdatedAt = LocalDateTime.now();

        if (!track.isPersistent()) {
            track.persist();
            trackFile.trackId = track.id;
        }

        return toMusicResponse(trackFile);
    }

    @PUT
    @Path("/{musicId}/artwork")
    @Consumes(MediaType.APPLICATION_JSON)
    public MusicArtworkResponse bindArtwork(
            @PathParam("musicId") Long musicId,
            ArtworkBindRequest request
    ) {
        return artworkService.bind(musicId, request == null ? null : request.artworkId());
    }

    @POST
    @Path("/{musicId}/artwork/import")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public MusicArtworkResponse importArtwork(
            @PathParam("musicId") Long musicId,
            @RestForm("file") FileUpload file
    ) {
        return artworkService.importAndBind(musicId, file);
    }

    @DELETE
    @Path("/{musicId}/artwork")
    public MusicArtworkResponse unbindArtwork(@PathParam("musicId") Long musicId) {
        return artworkService.unbind(musicId);
    }

    private MusicResponse toMusicResponse(TrackFile trackFile) {
        LyricService.PrimaryLyricSummary lyric = lyricService.primaryLyricsForSongIds(List.of(trackFile.id)).get(trackFile.id);
        ArtworkService.PrimaryArtworkSummary artwork = artworkService.primaryArtworkForMusicIds(List.of(trackFile.id)).get(trackFile.id);
        return MusicResponse.from(
                trackFile,
                lyric == null ? "NO_LYRIC" : lyric.lyricStatus(),
                lyric == null ? null : lyric.lyricId(),
                artwork == null ? "MISSING" : "BOUND",
                artwork == null ? null : artwork.artworkId(),
                artwork == null ? null : artwork.artworkPreviewUrl(),
                artwork == null ? null : artwork.artworkFileName(),
                artwork == null ? null : artwork.artworkFileExists()
        );
    }

    private MusicResponse toMusicResponse(
            TrackFile trackFile,
            Track track,
            LyricService.PrimaryLyricSummary lyric,
            ArtworkService.PrimaryArtworkSummary artwork
    ) {
        return MusicResponse.from(
                trackFile,
                track,
                lyric == null ? "NO_LYRIC" : lyric.lyricStatus(),
                lyric == null ? null : lyric.lyricId(),
                artwork == null ? "MISSING" : "BOUND",
                artwork == null ? null : artwork.artworkId(),
                artwork == null ? null : artwork.artworkPreviewUrl(),
                artwork == null ? null : artwork.artworkFileName(),
                artwork == null ? null : artwork.artworkFileExists()
        );
    }

    private Track trackOf(TrackFile trackFile, Map<Long, Track> tracksById) {
        if (trackFile.trackId == null) {
            return null;
        }
        return tracksById.get(trackFile.trackId);
    }

    private Track trackOf(TrackFile trackFile) {
        if (trackFile.trackId == null) {
            return new Track();
        }
        Track track = Track.findById(trackFile.trackId);
        if (track == null) {
            return new Track();
        }
        return track;
    }

    private Map<Long, Track> tracksById(List<TrackFile> trackFiles) {
        List<Long> trackIds = trackFiles.stream()
                .map(trackFile -> trackFile.trackId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (trackIds.isEmpty()) {
            return new HashMap<>();
        }
        return Track.<Track>list("id in ?1", trackIds).stream()
                .collect(Collectors.toMap(track -> track.id, Function.identity()));
    }

    private String resolveScanPath(MusicScanRequest request) {
        if (request != null && request.path() != null && !request.path().isBlank()) {
            return request.path().trim();
        }
        if (defaultScanPath == null || defaultScanPath.isBlank()) {
            List<String> musicDirs = config.musicDirs();
            if (musicDirs == null || musicDirs.isEmpty()) {
                throw new BadRequestException("No default music scan path configured");
            }
            return musicDirs.get(0);
        }
        return defaultScanPath;
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

    private int resolveArtistPage(Integer page) {
        if (page == null) {
            return 1;
        }
        if (page < 1) {
            throw new BadRequestException("page must be greater than or equal to 1");
        }
        return page;
    }

    private int resolveArtistPageSize(Integer pageSize) {
        if (pageSize == null) {
            return 20;
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BadRequestException("pageSize must be between 1 and 100");
        }
        return pageSize;
    }

    private String resolveArtistSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "trackCountDesc";
        }
        String value = sort.trim();
        if (!Set.of("trackCountDesc", "nameAsc", "albumCountDesc", "metadataIncompleteDesc").contains(value)) {
            throw new BadRequestException("sort must be trackCountDesc, nameAsc, albumCountDesc, or metadataIncompleteDesc");
        }
        return value;
    }

    private String resolveAlbumSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return "trackCountDesc";
        }
        String value = sort.trim();
        if (!Set.of("trackCountDesc", "nameAsc", "yearDesc", "metadataIncompleteDesc").contains(value)) {
            throw new BadRequestException("sort must be trackCountDesc, nameAsc, yearDesc, or metadataIncompleteDesc");
        }
        return value;
    }

    private Comparator<ArtistResponse> artistComparator(String sort) {
        Comparator<ArtistResponse> byName = Comparator.comparing(ArtistResponse::artist, String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case "nameAsc" -> byName;
            case "albumCountDesc" -> Comparator.comparingLong(ArtistResponse::albumCount).reversed().thenComparing(byName);
            case "metadataIncompleteDesc" -> Comparator.comparingLong(ArtistResponse::metadataIncompleteCount).reversed().thenComparing(byName);
            default -> Comparator.comparingLong(ArtistResponse::trackCount).reversed().thenComparing(byName);
        };
    }

    private Comparator<AlbumResponse> albumComparator(String sort) {
        Comparator<AlbumResponse> byName = Comparator
                .comparing(AlbumResponse::album, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(AlbumResponse::albumArtist, String.CASE_INSENSITIVE_ORDER);
        return switch (sort) {
            case "nameAsc" -> byName;
            case "yearDesc" -> Comparator
                    .comparing(AlbumResponse::year, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(byName);
            case "metadataIncompleteDesc" -> Comparator.comparingLong(AlbumResponse::metadataIncompleteCount).reversed().thenComparing(byName);
            default -> Comparator.comparingLong(AlbumResponse::trackCount).reversed().thenComparing(byName);
        };
    }

    private List<TrackFile> activeTrackFiles() {
        return TrackFile.list(
                "deleteStatus is null or deleteStatus = ?1",
                DELETE_STATUS_ACTIVE
        );
    }

    private Map<String, ArtistAggregate> artistAggregates(List<TrackFile> trackFiles) {
        Map<Long, Track> tracksById = tracksById(trackFiles);
        List<Long> musicIds = trackFiles.stream().map(trackFile -> trackFile.id).toList();
        Map<Long, LyricService.PrimaryLyricSummary> lyricsBySongId = lyricService.primaryLyricsForSongIds(musicIds);
        Map<Long, ArtworkService.PrimaryArtworkSummary> artworksByMusicId = artworkService.primaryArtworkForMusicIds(musicIds);

        Map<String, ArtistAggregate> aggregates = new LinkedHashMap<>();
        for (TrackFile trackFile : trackFiles) {
            Track track = trackOf(trackFile, tracksById);
            String artist = artistDisplayName(track);
            String artistKey = artistKeyOf(artist);
            ArtistAggregate aggregate = aggregates.computeIfAbsent(artistKey, ignored -> new ArtistAggregate(artist, artistKey));
            boolean hasLyrics = lyricsBySongId.containsKey(trackFile.id);
            boolean hasArtwork = artworksByMusicId.containsKey(trackFile.id);
            boolean metadataIncomplete = track == null || metadataIncomplete(track);
            aggregate.addTrack(trackFile, track, hasLyrics, hasArtwork, metadataIncomplete);
        }
        return aggregates;
    }

    private Map<String, AlbumAggregate> albumAggregates(List<TrackFile> trackFiles) {
        Map<Long, Track> tracksById = tracksById(trackFiles);
        List<Long> musicIds = trackFiles.stream().map(trackFile -> trackFile.id).toList();
        Map<Long, LyricService.PrimaryLyricSummary> lyricsBySongId = lyricService.primaryLyricsForSongIds(musicIds);
        Map<Long, ArtworkService.PrimaryArtworkSummary> artworksByMusicId = artworkService.primaryArtworkForMusicIds(musicIds);

        Map<String, AlbumAggregate> aggregates = new LinkedHashMap<>();
        for (TrackFile trackFile : trackFiles) {
            Track track = trackOf(trackFile, tracksById);
            String album = albumDisplayName(track);
            String albumKey = albumKeyOf(album);
            String albumArtist = albumArtistDisplayName(track);
            String artistKey = artistKeyOf(albumArtist);
            String aggregateKey = albumAggregateKey(albumKey, artistKey);
            AlbumAggregate aggregate = aggregates.computeIfAbsent(
                    aggregateKey,
                    ignored -> new AlbumAggregate(album, albumKey, albumArtist, artistKey)
            );
            boolean hasLyrics = lyricsBySongId.containsKey(trackFile.id);
            boolean hasArtwork = artworksByMusicId.containsKey(trackFile.id);
            boolean metadataIncomplete = track == null || metadataIncomplete(track);
            aggregate.addTrack(trackFile, track, hasLyrics, hasArtwork, metadataIncomplete);
        }
        return aggregates;
    }

    private static String artistDisplayName(Track track) {
        if (track == null || !hasText(track.artist)) {
            return UNKNOWN_ARTIST;
        }
        return track.artist.trim();
    }

    private static String albumArtistDisplayName(Track track) {
        if (track != null && hasText(track.albumArtist)) {
            return track.albumArtist.trim();
        }
        return artistDisplayName(track);
    }

    private static String artistKeyOf(String artist) {
        if (UNKNOWN_ARTIST.equals(artist)) {
            return UNKNOWN_ARTIST_KEY;
        }
        return keyOf(artist);
    }

    private static String canonicalArtistKey(String artistKey) {
        if (artistKey == null || artistKey.isBlank()) {
            throw new BadRequestException("artistKey must not be blank");
        }
        String value = artistKey.trim();
        if (UNKNOWN_ARTIST_KEY.equals(value)) {
            return UNKNOWN_ARTIST_KEY;
        }
        return artistKeyOf(URLDecoder.decode(value, StandardCharsets.UTF_8));
    }

    private static String albumDisplayName(Track track) {
        if (track == null || !hasText(track.album)) {
            return UNKNOWN_ALBUM;
        }
        return track.album.trim();
    }

    private static String albumKeyOf(String album) {
        if (UNKNOWN_ALBUM.equals(album)) {
            return UNKNOWN_ALBUM_KEY;
        }
        return keyOf(album);
    }

    private static String canonicalAlbumKey(String albumKey) {
        if (albumKey == null || albumKey.isBlank()) {
            throw new BadRequestException("albumKey must not be blank");
        }
        String value = albumKey.trim();
        if (UNKNOWN_ALBUM_KEY.equals(value)) {
            return UNKNOWN_ALBUM_KEY;
        }
        return albumKeyOf(URLDecoder.decode(value, StandardCharsets.UTF_8));
    }

    private static String keyOf(String value) {
        return URLEncoder.encode(value.trim().toLowerCase(Locale.ROOT), StandardCharsets.UTF_8);
    }

    private static String albumAggregateKey(String albumKey, String artistKey) {
        return albumKey + "|" + artistKey;
    }

    private boolean metadataIncomplete(Track track) {
        return !hasText(track.title) || !hasText(track.artist) || !hasText(track.album);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private FilterQuery buildFilterQuery(
            String keyword,
            String artistKey,
            String albumKey,
            Integer year,
            String genre,
            String metadata,
            Boolean hasLyrics,
            Boolean hasArtwork
    ) {
        StringBuilder query = new StringBuilder("(deleteStatus is null or deleteStatus = ?1)");
        List<Object> params = new ArrayList<>();
        params.add(DELETE_STATUS_ACTIVE);

        if (keyword != null && !keyword.isBlank()) {
            String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            int idx = params.size() + 1;
            query.append(" and (lower(fileName) like ?").append(idx)
                    .append(" or trackId in (select t.id from Track t where lower(t.title) like ?").append(idx)
                    .append(" or lower(t.artist) like ?").append(idx)
                    .append(" or lower(t.album) like ?").append(idx)
                    .append("))");
            params.add(like);
        }

        if (artistKey != null && !artistKey.isBlank()) {
            String key = canonicalArtistKey(artistKey);
            appendArtistKeyFilter(query, params, key, albumKey != null && !albumKey.isBlank());
        }

        if (albumKey != null && !albumKey.isBlank()) {
            String key = canonicalAlbumKey(albumKey);
            if (UNKNOWN_ALBUM_KEY.equals(key)) {
                query.append(" and (trackId is null or trackId in (select t.id from Track t where t.album is null or length(trim(t.album)) = 0))");
            } else {
                int idx = params.size() + 1;
                String albumName = URLDecoder.decode(key, StandardCharsets.UTF_8);
                query.append(" and trackId in (select t.id from Track t where lower(trim(t.album)) = ?")
                        .append(idx).append(")");
                params.add(albumName.toLowerCase(Locale.ROOT));
            }
        }

        if (year != null) {
            int idx = params.size() + 1;
            query.append(" and trackId in (select t.id from Track t where t.year = ?").append(idx).append(")");
            params.add(year);
        }

        if (genre != null && !genre.isBlank()) {
            int idx = params.size() + 1;
            query.append(" and trackId in (select t.id from Track t where lower(t.genre) = ?").append(idx).append(")");
            params.add(genre.trim().toLowerCase(Locale.ROOT));
        }

        if (metadata != null && !metadata.isBlank()) {
            String value = metadata.trim().toLowerCase(Locale.ROOT);
            if ("incomplete".equals(value)) {
                query.append(" and (trackId is null or trackId in (select t.id from Track t where ")
                        .append(metadataIncompletePredicate("t"))
                        .append("))");
            } else if ("complete".equals(value)) {
                query.append(" and trackId is not null and trackId in (select t.id from Track t where ")
                        .append(metadataCompletePredicate("t"))
                        .append(")");
            } else if (!"all".equals(value)) {
                throw new BadRequestException("metadata must be all, incomplete, or complete");
            }
        }

        if (hasLyrics != null) {
            if (hasLyrics) {
                query.append(" and id in (select sl.songId from SongLyric sl where sl.isPrimary = true)");
            } else {
                query.append(" and id not in (select sl.songId from SongLyric sl where sl.isPrimary = true)");
            }
        }

        if (hasArtwork != null) {
            if (hasArtwork) {
                query.append(" and id in (select mab.musicId from MusicArtworkBinding mab where mab.isPrimary = true and mab.relationType = '")
                        .append(ArtworkService.TRACK_COVER).append("')");
            } else {
                query.append(" and id not in (select mab.musicId from MusicArtworkBinding mab where mab.isPrimary = true and mab.relationType = '")
                        .append(ArtworkService.TRACK_COVER).append("')");
            }
        }

        return new FilterQuery(query.toString(), params);
    }

    private void appendArtistKeyFilter(StringBuilder query, List<Object> params, String key, boolean albumScoped) {
        if (UNKNOWN_ARTIST_KEY.equals(key)) {
            if (albumScoped) {
                query.append(" and (trackId is null or trackId in (select t.id from Track t where ")
                        .append(albumArtistUnknownPredicate("t"))
                        .append("))");
            } else {
                query.append(" and (trackId is null or trackId in (select t.id from Track t where t.artist is null or length(trim(t.artist)) = 0))");
            }
            return;
        }

        int idx = params.size() + 1;
        String artistName = URLDecoder.decode(key, StandardCharsets.UTF_8);
        if (albumScoped) {
            query.append(" and trackId in (select t.id from Track t where lower(trim(")
                    .append(albumArtistExpression("t"))
                    .append(")) = ?")
                    .append(idx)
                    .append(")");
        } else {
            query.append(" and trackId in (select t.id from Track t where lower(trim(t.artist)) = ?")
                    .append(idx)
                    .append(")");
        }
        params.add(artistName.toLowerCase(Locale.ROOT));
    }

    private boolean isActive(TrackFile trackFile) {
        return trackFile.deleteStatus == null || DELETE_STATUS_ACTIVE.equals(trackFile.deleteStatus);
    }

    private String metadataIncompletePredicate(String alias) {
        return "%s.title is null or length(trim(%s.title)) = 0 or %s.artist is null or length(trim(%s.artist)) = 0 or %s.album is null or length(trim(%s.album)) = 0"
                .formatted(alias, alias, alias, alias, alias, alias);
    }

    private String metadataCompletePredicate(String alias) {
        return "%s.title is not null and length(trim(%s.title)) > 0 and %s.artist is not null and length(trim(%s.artist)) > 0 and %s.album is not null and length(trim(%s.album)) > 0"
                .formatted(alias, alias, alias, alias, alias, alias);
    }

    private String albumArtistExpression(String alias) {
        return "case when %s.albumArtist is not null and length(trim(%s.albumArtist)) > 0 then %s.albumArtist else %s.artist end"
                .formatted(alias, alias, alias, alias);
    }

    private String albumArtistUnknownPredicate(String alias) {
        return "(%s.albumArtist is null or length(trim(%s.albumArtist)) = 0) and (%s.artist is null or length(trim(%s.artist)) = 0)"
                .formatted(alias, alias, alias, alias);
    }

    private void validateYear(Integer year) {
        if (year == null) {
            return;
        }
        int maxYear = Year.now().getValue() + 1;
        if (year < 1900 || year > maxYear) {
            throw new BadRequestException("year must be between 1900 and " + maxYear);
        }
    }

    private void validateTrackNo(Integer trackNo) {
        if (trackNo == null) {
            return;
        }
        if (trackNo <= 0) {
            throw new BadRequestException("trackNo must be greater than 0");
        }
    }

    private String cleanText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private TrackFile findMusic(Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        return trackFile;
    }

    private void requireTrashed(TrackFile trackFile) {
        if (!DELETE_STATUS_TRASHED.equals(trackFile.deleteStatus)) {
            throw new ConflictException("Music is not in trash");
        }
    }

    private DeletableMusicFile resolveDeletableMusicFile(String filePath) {
        java.nio.file.Path requestedPath = java.nio.file.Path.of(filePath);
        rejectPathTraversal(requestedPath, "Music file path");
        java.nio.file.Path realFilePath;
        try {
            realFilePath = requestedPath.toRealPath();
        } catch (IOException exception) {
            throw new ConflictException("Music file does not exist");
        }

        if (!Files.isRegularFile(realFilePath)) {
            throw new ConflictException("Music path must be a regular file");
        }

        java.nio.file.Path libraryRoot = config.musicDirs().stream()
                .map(java.nio.file.Path::of)
                .peek(path -> rejectPathTraversal(path, "Music library root"))
                .map(this::realLibraryRoot)
                .filter(root -> realFilePath.startsWith(root) && !realFilePath.equals(root))
                .findFirst()
                .orElse(null);
        if (libraryRoot == null) {
            throw new ConflictException("Music file is outside configured library roots");
        }

        java.nio.file.Path trashRoot = libraryRoot.resolve(TRASH_DIRECTORY_NAME).normalize();
        if (!trashRoot.startsWith(libraryRoot) || realFilePath.startsWith(trashRoot)) {
            throw new ConflictException("Music file is already inside the music vault trash");
        }
        return new DeletableMusicFile(realFilePath, libraryRoot);
    }

    private java.nio.file.Path realLibraryRoot(java.nio.file.Path root) {
        try {
            return root.toRealPath();
        } catch (IOException exception) {
            throw new ConflictException("Music library root does not exist");
        }
    }

    private void rejectPathTraversal(java.nio.file.Path path, String label) {
        for (java.nio.file.Path segment : path) {
            if ("..".equals(segment.toString())) {
                throw new ConflictException(label + " must not contain path traversal");
            }
        }
    }

    private TrashMusicFile resolveTrashMusicFile(TrackFile trackFile) {
        if (trackFile.trashPath == null || trackFile.trashPath.isBlank()) {
            throw new ConflictException("Trash path is empty");
        }
        java.nio.file.Path requestedPath = java.nio.file.Path.of(trackFile.trashPath);
        rejectPathTraversal(requestedPath, "Trash path");
        java.nio.file.Path realTrashPath;
        try {
            realTrashPath = requestedPath.toRealPath();
        } catch (IOException exception) {
            throw new ConflictException("Trash file does not exist");
        }
        if (!Files.isRegularFile(realTrashPath)) {
            throw new ConflictException("Trash path must be a regular file");
        }

        for (String configuredRoot : config.musicDirs()) {
            java.nio.file.Path libraryRoot = java.nio.file.Path.of(configuredRoot);
            rejectPathTraversal(libraryRoot, "Music library root");
            java.nio.file.Path realLibraryRoot = realLibraryRoot(libraryRoot);
            java.nio.file.Path trashRoot = realTrashRoot(realLibraryRoot);
            if (trashRoot != null && realTrashPath.startsWith(trashRoot) && !realTrashPath.equals(trashRoot)) {
                return new TrashMusicFile(realTrashPath, realLibraryRoot);
            }
        }
        throw new ConflictException("Trash file is outside the music vault trash");
    }

    private java.nio.file.Path realTrashRoot(java.nio.file.Path libraryRoot) {
        java.nio.file.Path trashRoot = libraryRoot.resolve(TRASH_DIRECTORY_NAME).normalize();
        if (!trashRoot.startsWith(libraryRoot)) {
            throw new ConflictException("Trash directory is outside configured library root");
        }
        try {
            return trashRoot.toRealPath();
        } catch (IOException exception) {
            return null;
        }
    }

    private java.nio.file.Path resolveRestoreTarget(TrackFile trackFile, java.nio.file.Path libraryRoot) {
        String originalPathValue = trackFile.originalPath == null || trackFile.originalPath.isBlank()
                ? trackFile.filePath
                : trackFile.originalPath;
        java.nio.file.Path originalPath = java.nio.file.Path.of(originalPathValue);
        rejectPathTraversal(originalPath, "Original music file path");
        java.nio.file.Path target = originalPath.toAbsolutePath().normalize();
        if (!target.startsWith(libraryRoot) || target.equals(libraryRoot)) {
            throw new ConflictException("Original music file path is outside configured library root");
        }
        java.nio.file.Path trashRoot = libraryRoot.resolve(TRASH_DIRECTORY_NAME).normalize();
        if (target.startsWith(trashRoot)) {
            throw new ConflictException("Original music file path must not be inside trash");
        }
        if (target.getParent() == null) {
            throw new ConflictException("Original music file path is invalid");
        }
        return target;
    }

    private void verifyExistingAncestorInsideLibrary(java.nio.file.Path target, java.nio.file.Path libraryRoot) {
        java.nio.file.Path parent = target.getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }
        if (parent == null) {
            throw new ConflictException("Original music file path is invalid");
        }
        try {
            java.nio.file.Path realParent = parent.toRealPath();
            if (!realParent.startsWith(libraryRoot)) {
                throw new ConflictException("Original music file directory is outside configured library root");
            }
        } catch (IOException exception) {
            throw new ConflictException("Original music file directory cannot be resolved");
        }
    }

    private void ensureRestoreParentInsideLibrary(java.nio.file.Path originalPath, java.nio.file.Path libraryRoot) throws IOException {
        java.nio.file.Path realParent = originalPath.getParent().toRealPath();
        if (!realParent.startsWith(libraryRoot)) {
            throw new ConflictException("Original music file directory is outside configured library root");
        }
    }

    private java.nio.file.Path uniqueTrashPath(java.nio.file.Path libraryRoot, Long musicId, String fileName) {
        java.nio.file.Path trashRoot = libraryRoot.resolve(TRASH_DIRECTORY_NAME).normalize();
        if (!trashRoot.startsWith(libraryRoot)) {
            throw new ConflictException("Trash directory is outside configured library root");
        }
        java.nio.file.Path directory = trashRoot.resolve(String.valueOf(musicId)).normalize();
        if (!directory.startsWith(trashRoot)) {
            throw new ConflictException("Trash directory is unsafe");
        }
        java.nio.file.Path target = directory.resolve(fileName).normalize();
        if (!target.startsWith(directory)) {
            throw new ConflictException("Music file name is unsafe");
        }
        if (!Files.exists(target)) {
            return target;
        }

        int dotIndex = fileName.lastIndexOf('.');
        String baseName = dotIndex <= 0 ? fileName : fileName.substring(0, dotIndex);
        String extension = dotIndex <= 0 ? "" : fileName.substring(dotIndex);
        int suffix = 1;
        do {
            target = directory.resolve(baseName + "-" + suffix + extension).normalize();
            if (!target.startsWith(directory)) {
                throw new ConflictException("Trash file name is unsafe");
            }
            suffix++;
        } while (Files.exists(target) && suffix <= 1000);
        if (Files.exists(target)) {
            throw new ConflictException("Cannot allocate trash file name");
        }
        return target;
    }

    private void moveToTrash(java.nio.file.Path sourcePath, java.nio.file.Path trashPath) throws IOException {
        try {
            Files.move(sourcePath, trashPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(sourcePath, trashPath);
        }
    }

    private void moveFromTrash(java.nio.file.Path trashPath, java.nio.file.Path originalPath) throws IOException {
        try {
            Files.move(trashPath, originalPath, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(trashPath, originalPath);
        }
    }

    private record FilterQuery(String query, List<Object> parameters) {
    }

    private static final class ArtistAggregate {
        private final String artist;
        private final String artistKey;
        private long trackCount;
        private long lyricsCount;
        private long artworkCount;
        private long metadataIncompleteCount;
        private final Set<String> albumNames = new HashSet<>();
        private final Map<String, AlbumAggregate> albums = new LinkedHashMap<>();

        private ArtistAggregate(String artist, String artistKey) {
            this.artist = artist;
            this.artistKey = artistKey;
        }

        private void addTrack(
                TrackFile trackFile,
                Track track,
                boolean hasLyrics,
                boolean hasArtwork,
                boolean metadataIncomplete
        ) {
            trackCount++;
            if (hasLyrics) {
                lyricsCount++;
            }
            if (hasArtwork) {
                artworkCount++;
            }
            if (metadataIncomplete) {
                metadataIncompleteCount++;
            }

            String album = albumDisplayName(track);
            String albumKey = albumKeyOf(album);
            if (track != null && hasText(track.album)) {
                albumNames.add(track.album.trim().toLowerCase(Locale.ROOT));
            }
            AlbumAggregate albumAggregate = albums.computeIfAbsent(
                    albumKey,
                    ignored -> new AlbumAggregate(album, albumKey, artist, artistKey)
            );
            albumAggregate.addTrack(trackFile, track, hasLyrics, hasArtwork, metadataIncomplete);
        }

        private ArtistResponse toResponse() {
            return new ArtistResponse(
                    artist,
                    artistKey,
                    trackCount,
                    albumNames.size(),
                    lyricsCount,
                    artworkCount,
                    metadataIncompleteCount
            );
        }

        private ArtistDetailResponse toDetailResponse() {
            List<ArtistAlbumResponse> albumItems = albums.values().stream()
                    .map(AlbumAggregate::toResponse)
                    .sorted(Comparator.comparing(ArtistAlbumResponse::album, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            return new ArtistDetailResponse(
                    artist,
                    artistKey,
                    trackCount,
                    albumNames.size(),
                    lyricsCount,
                    artworkCount,
                    metadataIncompleteCount,
                    albumItems
            );
        }
    }

    private static final class AlbumAggregate {
        private final String album;
        private final String albumKey;
        private final String albumArtist;
        private final String artistKey;
        private long trackCount;
        private long lyricsCount;
        private long artworkCount;
        private long metadataIncompleteCount;
        private Integer year;
        private Long coverMusicId;
        private Long sampleMusicId;

        private AlbumAggregate(String album, String albumKey, String albumArtist, String artistKey) {
            this.album = album;
            this.albumKey = albumKey;
            this.albumArtist = albumArtist;
            this.artistKey = artistKey;
        }

        private void addTrack(
                TrackFile trackFile,
                Track track,
                boolean hasLyrics,
                boolean hasArtwork,
                boolean metadataIncomplete
        ) {
            trackCount++;
            if (sampleMusicId == null) {
                sampleMusicId = trackFile.id;
            }
            if (track != null && track.year != null && (year == null || track.year < year)) {
                year = track.year;
            }
            if (hasLyrics) {
                lyricsCount++;
            }
            if (hasArtwork) {
                artworkCount++;
                if (coverMusicId == null) {
                    coverMusicId = trackFile.id;
                }
            }
            if (metadataIncomplete) {
                metadataIncompleteCount++;
            }
        }

        private ArtistAlbumResponse toResponse() {
            return new ArtistAlbumResponse(
                    album,
                    albumKey,
                    year,
                    trackCount,
                    lyricsCount,
                    artworkCount,
                    metadataIncompleteCount,
                    coverMusicId == null ? sampleMusicId : coverMusicId,
                    sampleMusicId
            );
        }

        private AlbumResponse toAlbumResponse() {
            return new AlbumResponse(
                    album,
                    albumKey,
                    albumArtist,
                    artistKey,
                    year,
                    trackCount,
                    lyricsCount,
                    artworkCount,
                    metadataIncompleteCount,
                    coverMusicId == null ? sampleMusicId : coverMusicId
            );
        }
    }

    private record DeletableMusicFile(
            java.nio.file.Path sourcePath,
            java.nio.file.Path libraryRoot
    ) {
    }

    private record TrashMusicFile(
            java.nio.file.Path trashPath,
            java.nio.file.Path libraryRoot
    ) {
    }
}
