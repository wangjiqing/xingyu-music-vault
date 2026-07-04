package com.xingyu.musicvault.api;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    @Inject
    ApplicationInfo applicationInfo;

    @GET
    public HealthResponse health() {
        return new HealthResponse("ok", "xingyu-music-vault", applicationInfo.version());
    }

    public record HealthResponse(String status, String service, String version) {
    }
}
