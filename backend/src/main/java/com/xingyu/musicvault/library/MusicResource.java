package com.xingyu.musicvault.library;

import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkBindRequest;
import com.xingyu.musicvault.artwork.ArtworkDtos.MusicArtworkResponse;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.MusicDtos.MusicMetadataUpdateRequest;
import com.xingyu.musicvault.library.MusicDtos.MusicResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicScanAccepted;
import com.xingyu.musicvault.library.MusicDtos.MusicScanRequest;
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

        PanacheQuery<TrackFile> query = TrackFile.findAll(Sort.descending("createdAt"));
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
    @Path("/{id}")
    public MusicResponse get(@PathParam("id") Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        return toMusicResponse(trackFile);
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
        track.title = title == null ? titleFromFileName(trackFile.fileName) : title;
        track.normalizedTitle = track.title.toLowerCase(Locale.ROOT);
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

    private String titleFromFileName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        String title = dotIndex <= 0 ? fileName : fileName.substring(0, dotIndex);
        title = title.trim();
        return title.isEmpty() ? "Untitled" : title;
    }
}
