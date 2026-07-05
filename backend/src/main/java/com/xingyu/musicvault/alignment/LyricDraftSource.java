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
@Table(name = "lyric_draft_sources")
public class LyricDraftSource extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "draft_id", nullable = false)
    public Long draftId;

    @Column(nullable = false, length = 32)
    public String provider;

    @Column(nullable = false, columnDefinition = "text")
    public String query;

    @Column(nullable = false, columnDefinition = "text")
    public String title;

    @Column(nullable = false, columnDefinition = "text")
    public String url;

    @Column(nullable = false, columnDefinition = "text")
    public String domain;

    @Column(name = "selected_by", nullable = false, columnDefinition = "text")
    public String selectedBy;

    @Column(name = "selected_at", nullable = false)
    public LocalDateTime selectedAt;

    @PrePersist
    void prePersist() {
        if (selectedAt == null) {
            selectedAt = LocalDateTime.now();
        }
    }
}
