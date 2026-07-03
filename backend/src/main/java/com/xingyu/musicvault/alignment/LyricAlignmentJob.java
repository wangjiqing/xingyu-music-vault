package com.xingyu.musicvault.alignment;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "lyric_alignment_jobs")
public class LyricAlignmentJob extends PanacheEntityBase {
    @Id
    @Column(length = 36)
    public String id;

    @Column(name = "song_id", nullable = false)
    public Long songId;

    @Column(name = "lyric_id", nullable = false)
    public Long lyricId;

    @Column(nullable = false, length = 32)
    public String status;

    @Column(name = "review_status", nullable = false, length = 32)
    public String reviewStatus;

    @Column(name = "import_status", nullable = false, length = 32)
    public String importStatus;

    @Column(name = "audio_relative_path", nullable = false, columnDefinition = "text")
    public String audioRelativePath;

    @Column(name = "worker_audio_path", nullable = false, columnDefinition = "text")
    public String workerAudioPath;

    @Column(name = "trusted_lyrics_hash", nullable = false, length = 64)
    public String trustedLyricsHash;

    @Column(name = "trusted_lyrics_snapshot", nullable = false, columnDefinition = "text")
    public String trustedLyricsSnapshot;

    @Column(name = "request_snapshot_json", nullable = false, columnDefinition = "text")
    public String requestSnapshotJson;

    @Column(name = "job_dir", nullable = false, columnDefinition = "text")
    public String jobDir;

    @Column(name = "error_message", columnDefinition = "text")
    public String errorMessage;

    @Column(name = "result_summary_json", columnDefinition = "text")
    public String resultSummaryJson;

    @Column(name = "worker_outcome", length = 32)
    public String workerOutcome;

    @Column(name = "worker_status_json", columnDefinition = "text")
    public String workerStatusJson;

    @Column(name = "alignment_json_hash", length = 64)
    public String alignmentJsonHash;

    @Column(name = "lrc_hash", length = 64)
    public String lrcHash;

    @Column(name = "swlrc_hash", length = 64)
    public String swlrcHash;

    @Column(name = "report_hash", length = 64)
    public String reportHash;

    @Column(name = "result_available", nullable = false)
    public boolean resultAvailable;

    @Column(name = "sync_message", columnDefinition = "text")
    public String syncMessage;

    @Column(name = "created_by", nullable = false, columnDefinition = "text")
    public String createdBy;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "queued_at")
    public LocalDateTime queuedAt;

    @Column(name = "started_at")
    public LocalDateTime startedAt;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;

    @Column(name = "failed_at")
    public LocalDateTime failedAt;

    @Column(name = "reviewed_by", columnDefinition = "text")
    public String reviewedBy;

    @Column(name = "reviewed_at")
    public LocalDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "text")
    public String reviewNote;

    @Column(name = "imported_by", columnDefinition = "text")
    public String importedBy;

    @Column(name = "imported_at")
    public LocalDateTime importedAt;

    @Column(name = "import_error_message", columnDefinition = "text")
    public String importErrorMessage;

    @Column(name = "imported_lyric_id")
    public Long importedLyricId;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
