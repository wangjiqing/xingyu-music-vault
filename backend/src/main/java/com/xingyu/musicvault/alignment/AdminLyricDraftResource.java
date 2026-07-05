package com.xingyu.musicvault.alignment;

import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ArtifactContent;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ConfirmLyricDraftRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.ConfirmLyricDraftResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.LyricDraftResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.LyricDraftSourceRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.LyricDraftSourceResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.RejectLyricDraftRequest;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.UpdateLyricDraftRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/lyric-draft-jobs")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminLyricDraftResource {
    @Inject
    LyricAlignmentService service;

    @GET
    @Path("/{jobId}/draft")
    public LyricDraftResponse getDraft(@PathParam("jobId") String jobId) {
        return service.getDraft(jobId);
    }

    @GET
    @Path("/{jobId}/artifacts/cleaned")
    public Response getCleanedTranscript(@PathParam("jobId") String jobId) {
        return artifactResponse(service.getDraftArtifact(jobId, LyricAlignmentService.DraftArtifact.CLEANED));
    }

    @GET
    @Path("/{jobId}/artifacts/raw")
    public Response getRawTranscript(@PathParam("jobId") String jobId) {
        return artifactResponse(service.getDraftArtifact(jobId, LyricAlignmentService.DraftArtifact.RAW));
    }

    @GET
    @Path("/{jobId}/artifacts/segments")
    public Response getSegments(@PathParam("jobId") String jobId) {
        return artifactResponse(service.getDraftArtifact(jobId, LyricAlignmentService.DraftArtifact.SEGMENTS));
    }

    @GET
    @Path("/{jobId}/artifacts/report")
    public Response getReport(@PathParam("jobId") String jobId) {
        return artifactResponse(service.getDraftArtifact(jobId, LyricAlignmentService.DraftArtifact.REPORT));
    }

    @PUT
    @Path("/{jobId}/draft")
    @Consumes(MediaType.APPLICATION_JSON)
    public LyricDraftResponse updateDraft(@PathParam("jobId") String jobId, UpdateLyricDraftRequest request) {
        return service.updateDraft(jobId, request);
    }

    @POST
    @Path("/{jobId}/confirm")
    @Consumes(MediaType.APPLICATION_JSON)
    public ConfirmLyricDraftResponse confirmDraft(@PathParam("jobId") String jobId, ConfirmLyricDraftRequest request) {
        return service.confirmDraft(jobId, request);
    }

    @POST
    @Path("/{jobId}/reject")
    @Consumes(MediaType.APPLICATION_JSON)
    public LyricDraftResponse rejectDraft(@PathParam("jobId") String jobId, RejectLyricDraftRequest request) {
        return service.rejectDraft(jobId, request);
    }

    @POST
    @Path("/{jobId}/sources")
    @Consumes(MediaType.APPLICATION_JSON)
    public LyricDraftSourceResponse addSource(@PathParam("jobId") String jobId, LyricDraftSourceRequest request) {
        return service.addDraftSource(jobId, request);
    }

    private Response artifactResponse(ArtifactContent artifact) {
        return Response.ok(artifact.content(), artifact.mediaType())
                .header("Content-Disposition", "inline; filename=\"" + artifact.fileName() + "\"")
                .build();
    }
}
