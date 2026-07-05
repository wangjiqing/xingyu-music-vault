package com.xingyu.musicvault.settings;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_settings")
public class AppSetting extends PanacheEntityBase {
    @Id
    @Column(name = "setting_key", length = 128)
    public String key;

    @Column(name = "setting_value_encrypted", columnDefinition = "text")
    public String encryptedValue;

    @Column(nullable = false)
    public boolean enabled = true;

    @Column(name = "updated_by", columnDefinition = "text")
    public String updatedBy;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "last_error", columnDefinition = "text")
    public String lastError;

    @Column(name = "last_checked_at")
    public LocalDateTime lastCheckedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
