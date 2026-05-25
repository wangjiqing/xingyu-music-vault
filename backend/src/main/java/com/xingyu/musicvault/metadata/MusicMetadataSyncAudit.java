package com.xingyu.musicvault.metadata;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "music_metadata_sync_audit")
public class MusicMetadataSyncAudit extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "batch_id")
    public String batchId;

    @Column(name = "music_id", nullable = false, columnDefinition = "integer")
    public Long musicId;

    @Column(name = "file_path")
    public String filePath;

    @Column(nullable = false)
    public String direction;

    @Column(name = "source_type", nullable = false)
    public String sourceType;

    @Column(name = "target_type", nullable = false)
    public String targetType;

    @Column(nullable = false)
    public String mode;

    @Column(name = "operation_type", nullable = false)
    public String operationType;

    @Column(name = "before_database_json")
    public String beforeDatabaseJson;

    @Column(name = "after_database_json")
    public String afterDatabaseJson;

    @Column(name = "before_file_json")
    public String beforeFileJson;

    @Column(name = "after_file_json")
    public String afterFileJson;

    @Column(name = "changed_fields_json")
    public String changedFieldsJson;

    @Column(nullable = false)
    public String status;

    @Column(name = "error_message")
    public String errorMessage;

    @Column(name = "rollback_status")
    public String rollbackStatus = "NOT_ROLLED_BACK";

    @Column(name = "rollback_of_audit_id", columnDefinition = "integer")
    public Long rollbackOfAuditId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "created_by")
    public String createdBy;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (rollbackStatus == null || rollbackStatus.isBlank()) {
            rollbackStatus = "NOT_ROLLED_BACK";
        }
    }
}
