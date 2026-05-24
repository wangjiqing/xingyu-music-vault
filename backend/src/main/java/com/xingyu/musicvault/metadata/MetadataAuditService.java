package com.xingyu.musicvault.metadata;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.metadata.MetadataDtos.MetadataAuditCreateRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MetadataAuditService {
    @Inject
    ObjectMapper objectMapper;

    public MusicMetadataSyncAudit create(MetadataAuditCreateRequest request) {
        MusicMetadataSyncAudit audit = new MusicMetadataSyncAudit();
        audit.batchId = request.batchId();
        audit.musicId = request.musicId();
        audit.filePath = request.filePath();
        audit.direction = request.direction();
        audit.sourceType = request.sourceType();
        audit.targetType = request.targetType();
        audit.mode = request.mode();
        audit.operationType = request.operationType();
        audit.beforeDatabaseJson = toJson(request.beforeDatabase());
        audit.afterDatabaseJson = toJson(request.afterDatabase());
        audit.beforeFileJson = toJson(request.beforeFile());
        audit.afterFileJson = toJson(request.afterFile());
        audit.changedFieldsJson = toJson(request.changedFields());
        audit.status = request.status();
        audit.errorMessage = request.errorMessage();
        audit.createdAt = request.createdAt();
        audit.createdBy = request.createdBy();
        audit.persistAndFlush();
        return audit;
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize metadata audit payload", exception);
        }
    }
}
