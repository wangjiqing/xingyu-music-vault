package com.xingyu.musicvault.lyrics;

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
@Table(name = "lyric_daily_recommendation")
public class LyricDailyRecommendation extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "recommendation_date", nullable = false)
    public String recommendationDate;

    @Column(name = "slot_no", nullable = false)
    public int slotNo;

    @Column(name = "music_id", nullable = false)
    public Long musicId;

    @Column(name = "recommendation_type", nullable = false)
    public String recommendationType;

    @Column(name = "action_status", nullable = false)
    public String actionStatus = "PENDING";

    @Column(name = "replaced_by_id")
    public Long replacedById;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "acted_at")
    public LocalDateTime actedAt;

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
