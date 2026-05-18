package com.xingyu.musicvault.library;

import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkBindRequest;
import com.xingyu.musicvault.artwork.ArtworkDtos.MusicArtworkResponse;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.MusicDtos.MusicFileResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicMetadataUpdateRequest;
import com.xingyu.musicvault.library.MusicDtos.MusicResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicScanAccepted;
import com.xingyu.musicvault.library.MusicDtos.MusicScanRequest;
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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
            @QueryParam("size") Integer size
    ) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);

        PanacheQuery<TrackFile> query = TrackFile.find(
                "deleteStatus is null or deleteStatus = ?1",
                Sort.descending("createdAt"),
                DELETE_STATUS_ACTIVE
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
    @Path("/{id}")
    public MusicResponse get(@PathParam("id") Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
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

        try {
            Files.createDirectories(originalPath.getParent());
            ensureRestoreParentInsideLibrary(originalPath, trashFile.libraryRoot());
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
