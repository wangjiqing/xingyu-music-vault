package com.xingyu.musicvault.openapi;

import com.xingyu.musicvault.openapi.OpenApiCredentialDtos.CreateCredentialRequest;
import com.xingyu.musicvault.openapi.OpenApiCredentialDtos.SetCredentialEnabledRequest;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/admin/openapi/credentials")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class OpenApiCredentialResource {
    @Inject
    OpenApiCredentialService credentialService;

    @POST
    public Response create(CreateCredentialRequest request) {
        return Response.status(Response.Status.CREATED)
                .entity(credentialService.create(request))
                .build();
    }

    @GET
    public Response list() {
        return Response.ok(credentialService.list()).build();
    }

    @PATCH
    @Path("/{id}/enabled")
    public Response setEnabled(@PathParam("id") Long id, SetCredentialEnabledRequest request) {
        if (request == null) {
            throw new BadRequestException("enabled must be provided");
        }
        return Response.ok(credentialService.setEnabled(id, request.enabled())).build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") Long id) {
        credentialService.delete(id);
        return Response.noContent().build();
    }
}
