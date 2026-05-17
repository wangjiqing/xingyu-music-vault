package com.xingyu.musicvault.library;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "track_files")
public class TrackFile extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "track_id")
    public Long trackId;

    @Column(name = "file_path", nullable = false, unique = true, columnDefinition = "text")
    public String filePath;

    @Column(name = "file_name", nullable = false)
    public String fileName;

    @Column(name = "file_ext", nullable = false, length = 16)
    public String fileExt;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @Column(name = "last_modified_at")
    public LocalDateTime lastModifiedAt;

    @Column(name = "scan_job_id")
    public Long scanJobId;

    @Column(name = "deleted_at")
    public LocalDateTime deletedAt;

    @Column(name = "trash_path", columnDefinition = "text")
    public String trashPath;

    @Column(name = "delete_status", nullable = false, length = 32)
    public String deleteStatus = "active";

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (deleteStatus == null || deleteStatus.isBlank()) {
            deleteStatus = "active";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
