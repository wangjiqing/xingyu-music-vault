package com.xingyu.musicvault.alignment;

import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobListItemResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ArtifactContent;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.CreateAlignmentJobRequest;
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

@Path("/api/lyric-alignment/jobs")
@Produces(MediaType.APPLICATION_JSON)
public class LyricAlignmentResource {
    @Inject
    LyricAlignmentService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public AlignmentJobResponse create(CreateAlignmentJobRequest request) {
        return service.create(request);
    }

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
