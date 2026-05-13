package com.xingyu.musicvault.library;

import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.MusicDtos.MusicResponse;
import com.xingyu.musicvault.library.MusicDtos.MusicScanAccepted;
import com.xingyu.musicvault.library.MusicDtos.MusicScanRequest;
import com.xingyu.musicvault.scan.LibraryScanService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

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
        List<MusicResponse> items = query.page(Page.of(pageValue, sizeValue)).list().stream()
                .map(MusicResponse::from)
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
        return MusicResponse.from(trackFile);
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
}
