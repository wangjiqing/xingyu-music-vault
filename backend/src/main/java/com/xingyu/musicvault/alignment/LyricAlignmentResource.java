package com.xingyu.musicvault.alignment;

import com.xingyu.musicvault.alignment.LyricAlignmentDtos.AlignmentJobResponse;
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
    public PageResponse<AlignmentJobResponse> list(
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
}
