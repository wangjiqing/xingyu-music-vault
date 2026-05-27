package com.xingyu.musicvault.openapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import io.quarkus.panache.common.Page;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class OpenApiChangeLogService {
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    @Inject
    OpenApiSyncStateService syncStateService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public OpenApiSyncChangeLog recordTrackChange(Long trackId, String changeType, List<String> changedFields) {
        return recordChange("track", trackId, changeType, changedFields);
    }

    @Transactional
    public OpenApiSyncChangeLog recordLyricsChange(Long trackId) {
        return recordChange("track", trackId, "updated", List.of("lyrics"));
    }

    @Transactional
    public OpenApiSyncChangeLog recordArtworkChange(Long trackId) {
        return recordChange("track", trackId, "updated", List.of("artwork"));
    }

    @Transactional
    public OpenApiSyncChangeLog recordChange(String entityType, Long entityId, String changeType, List<String> changedFields) {
        if (entityId == null) {
            return null;
        }
        OpenApiLibraryState state = syncStateService.state();
        state.libraryVersion++;
        state.lastChangedAt = nowIso();

        OpenApiSyncChangeLog changeLog = new OpenApiSyncChangeLog();
        changeLog.version = state.libraryVersion;
        changeLog.entityType = entityType;
        changeLog.entityId = entityId;
        changeLog.changeType = changeType;
        changeLog.changedFieldsJson = toJson(changedFields == null ? List.of() : changedFields);
        changeLog.changedAt = state.lastChangedAt;
        changeLog.persist();
        return changeLog;
    }

    public List<OpenApiSyncChangeLog> changesAfter(long sinceVersion, int limit) {
        return OpenApiSyncChangeLog.<OpenApiSyncChangeLog>find(
                "version > ?1 order by version asc",
                sinceVersion
        ).page(Page.of(0, limit)).list();
    }

    public boolean hasChangesAfter(long version) {
        return OpenApiSyncChangeLog.count("version > ?1", version) > 0;
    }

    public List<String> changedFields(OpenApiSyncChangeLog changeLog) {
        if (changeLog.changedFieldsJson == null || changeLog.changedFieldsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(changeLog.changedFieldsJson, STRING_LIST);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    private String toJson(List<String> changedFields) {
        try {
            return objectMapper.writeValueAsString(changedFields);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize OpenAPI changed fields", exception);
        }
    }

    private String nowIso() {
        return OffsetDateTime.now(ZoneId.systemDefault()).toString();
    }
}
