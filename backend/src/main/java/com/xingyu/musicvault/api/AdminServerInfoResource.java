package com.xingyu.musicvault.api;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin/server/info")
@Produces(MediaType.APPLICATION_JSON)
public class AdminServerInfoResource {
    @GET
    public ServerInfoResponse info() {
        return new ServerInfoResponse("xingyu-music-vault", "1.2.1");
    }

    public record ServerInfoResponse(String serviceName, String serviceVersion) {
    }
}
