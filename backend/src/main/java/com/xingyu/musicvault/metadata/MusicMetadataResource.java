package com.xingyu.musicvault.metadata;

import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataCompareRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataRollbackPreviewResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataRollbackRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataRollbackResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataSyncRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.BatchMetadataSyncResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditDetailResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditPageResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataCompareResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataRollbackPreviewResponse;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataRollbackRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataRollbackResult;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSyncRequest;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSyncResult;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@Path("/api/music")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class MusicMetadataResource {
    @Inject
    MusicMetadataSyncService musicMetadataSyncService;

    @Inject
    MetadataAuditService metadataAuditService;

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

    @GET
    @Path("/metadata/audits")
    public MetadataAuditPageResponse audits(
            @QueryParam("musicId") Long musicId,
            @QueryParam("batchId") String batchId,
            @QueryParam("direction") String direction,
            @QueryParam("status") String status,
            @QueryParam("rollbackStatus") String rollbackStatus,
            @QueryParam("keyword") String keyword,
            @QueryParam("startTime") String startTime,
            @QueryParam("endTime") String endTime,
            @QueryParam("page") Integer page,
            @QueryParam("pageSize") Integer pageSize
    ) {
        return metadataAuditService.list(
                musicId,
                batchId,
                direction,
                status,
                rollbackStatus,
                keyword,
                parseTime(startTime, "startTime"),
                parseTime(endTime, "endTime"),
                page,
                pageSize
        );
    }

    @GET
    @Path("/metadata/audits/{auditId}")
    public MetadataAuditDetailResponse auditDetail(@PathParam("auditId") Long auditId) {
        return metadataAuditService.detail(auditId);
    }

    @GET
    @Path("/metadata/audits/{auditId}/rollback-preview")
    public MetadataRollbackPreviewResponse rollbackPreview(@PathParam("auditId") Long auditId) {
        return metadataAuditService.rollbackPreview(auditId);
    }

    @POST
    @Path("/metadata/audits/{auditId}/rollback")
    public MetadataRollbackResult rollback(@PathParam("auditId") Long auditId, MetadataRollbackRequest request) {
        return metadataAuditService.rollback(auditId, request);
    }

    @POST
    @Path("/metadata/audits/rollback-preview")
    public BatchMetadataRollbackPreviewResponse rollbackPreviewBatch(BatchMetadataRollbackRequest request) {
        return metadataAuditService.rollbackPreviewBatch(request);
    }

    @POST
    @Path("/metadata/audits/rollback")
    public BatchMetadataRollbackResponse rollbackBatch(BatchMetadataRollbackRequest request) {
        return metadataAuditService.rollbackBatch(request);
    }

    private LocalDateTime parseTime(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BadRequestException(fieldName + " must be an ISO-8601 local date-time");
        }
    }
}
