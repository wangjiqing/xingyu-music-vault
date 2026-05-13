package com.xingyu.musicvault.job;

import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJobDtos.CreateScanJobRequest;
import com.xingyu.musicvault.job.ScanJobDtos.ScanJobResponse;
import com.xingyu.musicvault.scan.LibraryScanService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
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
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Set;

@Path("/api/scan-jobs")
@Produces(MediaType.APPLICATION_JSON)
public class ScanJobResource {
    private static final Logger LOG = Logger.getLogger(ScanJobResource.class);
    private static final Set<String> ALLOWED_STATUSES = Set.of("pending", "running", "completed", "failed");

    @Inject
    MusicVaultConfig config;

    @Inject
    LibraryScanService libraryScanService;

    @GET
    public PageResponse<ScanJobResponse> list(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @QueryParam("status") String status
    ) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);
        String normalizedStatus = normalizeStatus(status);

        PanacheQuery<ScanJob> query = normalizedStatus == null
                ? ScanJob.findAll(Sort.descending("createdAt"))
                : ScanJob.find("status", Sort.descending("createdAt"), normalizedStatus);
        long total = query.count();
        List<ScanJobResponse> items = query.page(Page.of(pageValue, sizeValue)).list().stream()
                .map(ScanJobResponse::from)
                .toList();
        LOG.debugf(
                "List scan jobs: page=%d size=%d status=%s returned=%d total=%d",
                pageValue,
                sizeValue,
                normalizedStatus,
                items.size(),
                total
        );
        return new PageResponse<>(items, pageValue, sizeValue, total);
    }

    @GET
    @Path("/{id}")
    public ScanJobResponse get(@PathParam("id") Long id) {
        return ScanJobResponse.from(findScanJob(id));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response create(CreateScanJobRequest request) {
        ScanJob scanJob = new ScanJob();
        scanJob.jobType = resolveJobType(request);
        scanJob.status = "pending";
        scanJob.musicDirs = resolveMusicDirs(request);
        scanJob.persist();
        LOG.infof("Created scan job: id=%d jobType=%s musicDirs=%s", scanJob.id, scanJob.jobType, scanJob.musicDirs);
        return Response.status(Response.Status.CREATED).entity(ScanJobResponse.from(scanJob)).build();
    }

    @POST
    @Path("/{id}/run")
    public ScanJobResponse run(@PathParam("id") Long id) {
        ScanJob scanJob = findScanJob(id);
        if ("running".equals(scanJob.status) || "completed".equals(scanJob.status)) {
            LOG.infof("Reject scan job run request: id=%d status=%s", scanJob.id, scanJob.status);
        } else {
            LOG.infof("Run scan job request accepted for service execution: id=%d status=%s", scanJob.id, scanJob.status);
        }
        return ScanJobResponse.from(libraryScanService.run(id));
    }

    private ScanJob findScanJob(Long id) {
        ScanJob scanJob = ScanJob.findById(id);
        if (scanJob == null) {
            throw new NotFoundException("Scan job not found");
        }
        return scanJob;
    }

    private String resolveJobType(CreateScanJobRequest request) {
        if (request == null || request.jobType() == null || request.jobType().isBlank()) {
            return "library_scan";
        }
        String jobType = request.jobType().trim();
        if (!"library_scan".equals(jobType)) {
            throw new BadRequestException("jobType must be library_scan");
        }
        return jobType;
    }

    private String resolveMusicDirs(CreateScanJobRequest request) {
        if (request != null && request.musicDirs() != null && !request.musicDirs().isEmpty()) {
            List<String> musicDirs = request.musicDirs().stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (musicDirs.isEmpty()) {
                throw new BadRequestException("musicDirs must contain at least one non-blank path");
            }
            return String.join(",", musicDirs);
        }
        return String.join(",", config.musicDirs());
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

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String normalized = status.trim();
        if (!ALLOWED_STATUSES.contains(normalized)) {
            throw new BadRequestException("status must be one of: pending, running, completed, failed");
        }
        return normalized;
    }
}
