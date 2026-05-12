package com.xingyu.musicvault.job;

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
@Table(name = "scan_jobs")
public class ScanJob extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "job_type", nullable = false)
    public String jobType;

    @Column(nullable = false)
    public String status;

    @Column(name = "music_dirs", columnDefinition = "text")
    public String musicDirs;

    @Column(name = "total_files", nullable = false)
    public long totalFiles;

    @Column(name = "scanned_files", nullable = false)
    public long scannedFiles;

    @Column(name = "new_files", nullable = false)
    public long newFiles;

    @Column(name = "updated_files", nullable = false)
    public long updatedFiles;

    @Column(name = "skipped_files", nullable = false)
    public long skippedFiles;

    @Column(name = "error_files", nullable = false)
    public long errorFiles;

    @Column(name = "error_message", columnDefinition = "text")
    public String errorMessage;

    @Column(name = "started_at")
    public LocalDateTime startedAt;

    @Column(name = "finished_at")
    public LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
