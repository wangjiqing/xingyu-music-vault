package com.xingyu.musicvault.job;

import com.xingyu.musicvault.config.MusicVaultConfig;
import io.quarkus.panache.common.Sort;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/scan-jobs")
@Produces(MediaType.APPLICATION_JSON)
public class ScanJobResource {
    @Inject
    MusicVaultConfig config;

    @GET
    public List<ScanJob> list() {
        return ScanJob.listAll(Sort.descending("createdAt"));
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Transactional
    public Response create(CreateScanJobRequest request) {
        ScanJob scanJob = new ScanJob();
        scanJob.jobType = request != null && request.jobType() != null ? request.jobType() : "library_scan";
        scanJob.status = "pending";
        scanJob.musicDirs = resolveMusicDirs(request);
        scanJob.persist();
        return Response.status(Response.Status.CREATED).entity(scanJob).build();
    }

    private String resolveMusicDirs(CreateScanJobRequest request) {
        if (request != null && request.musicDirs() != null && !request.musicDirs().isEmpty()) {
            return String.join(",", request.musicDirs());
        }
        return String.join(",", config.musicDirs());
    }

    public record CreateScanJobRequest(String jobType, List<String> musicDirs) {
    }
}
