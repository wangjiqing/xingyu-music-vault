package com.xingyu.musicvault.job;

import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJobDtos.CreateScanJobRequest;
import com.xingyu.musicvault.job.ScanJobDtos.ScanJobResponse;
import com.xingyu.musicvault.scan.LibraryScanService;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/scan-jobs")
@Produces(MediaType.APPLICATION_JSON)
public class ScanJobResource {
    @Inject
    MusicVaultConfig config;

    @Inject
    LibraryScanService libraryScanService;

    @GET
    public List<ScanJobResponse> list() {
        return ScanJob.<ScanJob>listAll(Sort.descending("createdAt")).stream()
                .map(ScanJobResponse::from)
                .toList();
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
        return Response.status(Response.Status.CREATED).entity(ScanJobResponse.from(scanJob)).build();
    }

    @POST
    @Path("/{id}/run")
    public ScanJobResponse run(@PathParam("id") Long id) {
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
}
