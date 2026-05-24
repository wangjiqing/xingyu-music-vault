package com.xingyu.musicvault.metadata;

import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataCompareRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataSyncRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataSyncResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataCompareResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSyncRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSyncResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/music")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MusicMetadataResource {
    @Inject
    MusicMetadataSyncService musicMetadataSyncService;

    @GET
    @Path("/{id}/metadata/compare")
    public MetadataCompareResponse compare(@PathParam("id") Long id) {
        return musicMetadataSyncService.compare(id);
    }

    @POST
    @Path("/{id}/metadata/apply-file-to-db")
    public MetadataSyncResult applyFileToDatabase(@PathParam("id") Long id, MetadataSyncRequest request) {
        return musicMetadataSyncService.applyFileToDatabase(id, request == null ? null : request.mode());
    }

    @POST
    @Path("/{id}/metadata/apply-db-to-file")
    public MetadataSyncResult applyDatabaseToFile(@PathParam("id") Long id, MetadataSyncRequest request) {
        return musicMetadataSyncService.applyDatabaseToFile(id, request == null ? null : request.mode());
    }

    @POST
    @Path("/metadata/compare")
    public List<MetadataCompareResponse> compareBatch(BatchMetadataCompareRequest request) {
        return musicMetadataSyncService.compareBatch(request);
    }

    @POST
    @Path("/metadata/apply-file-to-db")
    public BatchMetadataSyncResponse applyFileToDatabaseBatch(BatchMetadataSyncRequest request) {
        return musicMetadataSyncService.applyFileToDatabaseBatch(request);
    }

    @POST
    @Path("/metadata/apply-db-to-file")
    public BatchMetadataSyncResponse applyDatabaseToFileBatch(BatchMetadataSyncRequest request) {
        return musicMetadataSyncService.applyDatabaseToFileBatch(request);
    }
}
