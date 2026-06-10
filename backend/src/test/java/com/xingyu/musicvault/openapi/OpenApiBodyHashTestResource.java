package com.xingyu.musicvault.openapi;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/open/v1/body-hash-test")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OpenApiBodyHashTestResource {
    @POST
    public BodyHashResponse echo(String body) {
        return new BodyHashResponse(body == null ? 0 : body.length());
    }

    public record BodyHashResponse(int length) {
    }
}
