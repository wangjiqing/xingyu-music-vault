package com.xingyu.musicvault.alignment;

import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobResponse;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.CreateLyricDraftJobRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin/music")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminMusicLyricDraftResource {
    @Inject
    LyricAlignmentService service;

    @POST
    @Path("/{musicId}/lyric-draft-jobs")
    @Consumes(MediaType.APPLICATION_JSON)
    public AlignmentJobResponse createDraftJob(@PathParam("musicId") Long musicId, CreateLyricDraftJobRequest request) {
        return service.createDraftJob(musicId, request);
    }
}
