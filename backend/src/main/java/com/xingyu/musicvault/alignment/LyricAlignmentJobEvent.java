package com.xingyu.musicvault.alignment;

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
@Table(name = "lyric_alignment_job_events")
public class LyricAlignmentJobEvent extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "task_id", nullable = false, columnDefinition = "text")
    public String taskId;

    @Column(name = "music_id", nullable = false)
    public Long musicId;

    @Column(nullable = false, length = 32)
    public String action;

    @Column(nullable = false, columnDefinition = "text")
    public String operator;

    @Column(columnDefinition = "text")
    public String note;

    @Column(name = "before_status", columnDefinition = "text")
    public String beforeStatus;

    @Column(name = "after_status", columnDefinition = "text")
    public String afterStatus;

    @Column(name = "error_message", columnDefinition = "text")
    public String errorMessage;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
