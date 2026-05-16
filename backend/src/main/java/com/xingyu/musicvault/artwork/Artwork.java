package com.xingyu.musicvault.artwork;

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
@Table(name = "artworks")
public class Artwork extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "file_path", nullable = false, unique = true, columnDefinition = "text")
    public String filePath;

    @Column(name = "file_name", nullable = false)
    public String fileName;

    @Column(name = "file_ext", nullable = false, length = 16)
    public String fileExt;

    @Column(name = "mime_type", nullable = false, length = 64)
    public String mimeType;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @Column
    public Integer width;

    @Column
    public Integer height;

    @Column(nullable = false, length = 64)
    public String hash;

    @Column(name = "source_type", nullable = false, length = 32)
    public String sourceType;

    @Column(name = "source_path", columnDefinition = "text")
    public String sourcePath;

    @Column
    public String title;

    @Column(columnDefinition = "text")
    public String description;

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
