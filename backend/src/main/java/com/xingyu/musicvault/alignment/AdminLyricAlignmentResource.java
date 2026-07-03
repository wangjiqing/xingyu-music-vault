package com.xingyu.musicvault.alignment;

import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobListItemResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ArtifactContent;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ImportAlignmentJobRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ImportAlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ReviewAlignmentJobRequest;
import com.xingyu.musicvault.common.PageResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/lyric-alignment/jobs")
@Produces(MediaType.APPLICATION_JSON)
public class AdminLyricAlignmentResource {
    @Inject
    LyricAlignmentService service;

    @GET
    public PageResponse<AlignmentJobListItemResponse> list(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size,
            @QueryParam("status") String status
    ) {
        return service.list(page, size, status);
    }

    @GET
    @Path("/{id}")
    public AlignmentJobResponse get(@PathParam("id") String id) {
        return service.get(id);
    }

    @POST
    @Path("/{id}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    public AlignmentJobResponse approve(@PathParam("id") String id, ReviewAlignmentJobRequest request) {
        return service.approve(id, request);
    }

    @POST
    @Path("/{id}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    public AlignmentJobResponse reject(@PathParam("id") String id, ReviewAlignmentJobRequest request) {
        return service.reject(id, request);
    }

    @POST
    @Path("/{id}/import")
    @Consumes(MediaType.APPLICATION_JSON)
    public ImportAlignmentJobResponse importApproved(@PathParam("id") String id, ImportAlignmentJobRequest request) {
        return service.importApproved(id, request);
    }

    @GET
    @Path("/{id}/artifacts/report")
    public Response getReportArtifact(@PathParam("id") String id) {
        return artifactResponse(service.getArtifact(id, LyricAlignmentService.AlignmentArtifact.REPORT));
    }

    @GET
    @Path("/{id}/artifacts/lrc")
    public Response getLrcArtifact(@PathParam("id") String id) {
        return artifactResponse(service.getArtifact(id, LyricAlignmentService.AlignmentArtifact.LRC));
    }

    @GET
    @Path("/{id}/artifacts/swlrc")
    public Response getSwlrcArtifact(@PathParam("id") String id) {
        return artifactResponse(service.getArtifact(id, LyricAlignmentService.AlignmentArtifact.SWLRC));
    }

    @GET
    @Path("/{id}/artifacts/alignment")
    public Response getAlignmentArtifact(@PathParam("id") String id) {
        return artifactResponse(service.getArtifact(id, LyricAlignmentService.AlignmentArtifact.ALIGNMENT));
    }

    private Response artifactResponse(ArtifactContent artifact) {
        return Response.ok(artifact.content(), artifact.mediaType())
                .header("Content-Disposition", "inline; filename=\"" + artifact.fileName() + "\"")
                .build();
    }
}
