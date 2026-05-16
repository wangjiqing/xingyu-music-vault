package com.xingyu.musicvault.artwork;

import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkScanRequest;
import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkScanResponse;
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

@Produces(MediaType.APPLICATION_JSON)
@Path("/api")
public class ArtworkResource {
    @Inject
    ArtworkService artworkService;

    @GET
    @Path("/artworks")
    public PageResponse<ArtworkResponse> list(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        return artworkService.list(page, size);
    }

    @GET
    @Path("/artworks/{id}")
    public ArtworkResponse get(@PathParam("id") Long id) {
        return artworkService.get(id);
    }

    @GET
    @Path("/artworks/{id}/file")
    public Response file(@PathParam("id") Long id) {
        return artworkService.file(id);
    }

    @POST
    @Path("/artworks/scan")
    @Consumes(MediaType.APPLICATION_JSON)
    public ArtworkScanResponse scan(ArtworkScanRequest request) {
        return artworkService.scan(request == null ? null : request.path());
    }

}
