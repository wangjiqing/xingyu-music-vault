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
@Table(name = "tracks")
public class Track extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column
    public String title;

    @Column(name = "normalized_title")
    public String normalizedTitle;

    @Column
    public String artist;

    @Column
    public String album;

    @Column(name = "album_artist")
    public String albumArtist;

    @Column
    public Long duration;

    @Column
    public Integer year;

    @Column(name = "track_no")
    public Integer trackNo;

    @Column
    public String genre;

    @Column(name = "metadata_updated_at")
    public LocalDateTime metadataUpdatedAt;

    @Column(name = "metadata_status", nullable = false)
    public String metadataStatus = "pending";

    @Column(name = "lyrics_status", nullable = false)
    public String lyricsStatus = "pending";

    @Column(name = "artwork_status", nullable = false)
    public String artworkStatus = "pending";

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
