package com.xingyu.musicvault.alignment;

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
@Table(name = "lyric_drafts")
public class LyricDraft extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "job_id", nullable = false, unique = true, columnDefinition = "text")
    public String jobId;

    @Column(name = "music_id", nullable = false)
    public Long musicId;

    @Column(name = "original_text", nullable = false, columnDefinition = "text")
    public String originalText;

    @Column(name = "original_text_hash", nullable = false, length = 64)
    public String originalTextHash;

    @Column(name = "editable_text", nullable = false, columnDefinition = "text")
    public String editableText;

    @Column(name = "editable_text_hash", nullable = false, length = 64)
    public String editableTextHash;

    @Column(name = "draft_status", nullable = false, length = 32)
    public String draftStatus;

    @Column(name = "report_summary_json", columnDefinition = "text")
    public String reportSummaryJson;

    @Column(name = "transcript_raw_hash", length = 64)
    public String transcriptRawHash;

    @Column(name = "transcript_segments_hash", length = 64)
    public String transcriptSegmentsHash;

    @Column(name = "report_hash", length = 64)
    public String reportHash;

    @Column(name = "confirmed_trusted_lyrics_id")
    public Long confirmedTrustedLyricsId;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "edited_by", columnDefinition = "text")
    public String editedBy;

    @Column(name = "edited_at")
    public LocalDateTime editedAt;

    @Column(name = "confirmed_by", columnDefinition = "text")
    public String confirmedBy;

    @Column(name = "confirmed_at")
    public LocalDateTime confirmedAt;

    @Column(name = "rejected_by", columnDefinition = "text")
    public String rejectedBy;

    @Column(name = "rejected_at")
    public LocalDateTime rejectedAt;

    @Column(name = "reject_note", columnDefinition = "text")
    public String rejectNote;

    @Column(name = "error_message", columnDefinition = "text")
    public String errorMessage;

    @Column(name = "source_type", nullable = false, length = 32)
    public String sourceType;

    @Column(name = "source_metadata_json", columnDefinition = "text")
    public String sourceMetadataJson;

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
