package com.xingyu.musicvault.search;

import com.xingyu.musicvault.search.BraveSearchDtos.BraveSearchRequest;
import com.xingyu.musicvault.search.BraveSearchDtos.BraveSearchResponse;
import com.xingyu.musicvault.search.BraveSearchDtos.BraveSearchStatusResponse;
import com.xingyu.musicvault.search.BraveSearchDtos.SaveBraveKeyRequest;
import com.xingyu.musicvault.search.BraveSearchDtos.SetBraveEnabledRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/admin/brave-search")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class AdminBraveSearchResource {
    @Inject
    BraveSearchService service;

    @GET
    @Path("/status")
    public BraveSearchStatusResponse status() {
        return service.status();
    }

    @POST
    @Path("/key")
    public BraveSearchStatusResponse saveKey(SaveBraveKeyRequest request) {
        return service.saveKey(request);
    }

    @PATCH
    @Path("/enabled")
    public BraveSearchStatusResponse setEnabled(SetBraveEnabledRequest request) {
        return service.setEnabled(request);
    }

    @POST
    @Path("/test")
    public BraveSearchStatusResponse testConnection() {
        return service.testConnection();
    }

    @POST
    @Path("/search")
    public BraveSearchResponse search(BraveSearchRequest request) {
        return service.search(request);
    }
}
