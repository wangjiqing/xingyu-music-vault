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
@Table(name = "lyrics")
public class Lyric extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column
    public String title;

    @Column
    public String artist;

    @Column
    public String album;

    @Column(length = 16)
    public String language;

    @Column(name = "release_year")
    public Integer releaseYear;

    @Column(name = "source_type", nullable = false, length = 32)
    public String sourceType;

    @Column(name = "source_path", columnDefinition = "text")
    public String sourcePath;

    @Column(nullable = false, columnDefinition = "text")
    public String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    public String contentHash;

    @Column(nullable = false, length = 16)
    public String format;

    @Column(name = "parse_status", nullable = false, length = 32)
    public String parseStatus;

    @Column(name = "parse_message", columnDefinition = "text")
    public String parseMessage;

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
