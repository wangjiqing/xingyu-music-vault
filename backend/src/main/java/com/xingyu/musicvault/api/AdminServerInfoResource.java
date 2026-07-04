package com.xingyu.musicvault.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin/server/info")
@Produces(MediaType.APPLICATION_JSON)
public class AdminServerInfoResource {
    @Inject
    ApplicationInfo applicationInfo;

    @GET
    public ServerInfoResponse info() {
        return new ServerInfoResponse("xingyu-music-vault", applicationInfo.version());
    }

    public record ServerInfoResponse(String serviceName, String serviceVersion) {
    }
}
