package com.xingyu.musicvault.library;

import io.quarkus.panache.common.Sort;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/tracks")
@Produces(MediaType.APPLICATION_JSON)
public class TrackResource {
    @GET
    public List<Track> list() {
        return Track.listAll(Sort.descending("createdAt"));
    }
}
