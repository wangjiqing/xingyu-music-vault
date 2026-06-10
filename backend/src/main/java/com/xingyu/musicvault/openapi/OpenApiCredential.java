package com.xingyu.musicvault.openapi;

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
@Table(name = "openapi_credentials")
public class OpenApiCredential extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(nullable = false)
    public String name;

    @Column(name = "access_key", nullable = false, unique = true)
    public String accessKey;

    @Column(name = "secret_encrypted", nullable = false)
    public String secretEncrypted;

    @Column(name = "secret_fingerprint", nullable = false)
    public String secretFingerprint;

    @Column(name = "scopes_json", nullable = false)
    public String scopesJson;

    @Column(nullable = false)
    public boolean enabled = true;

    public String description;

    @Column(name = "expires_at")
    public LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @Column(name = "last_used_at")
    public LocalDateTime lastUsedAt;

    @Column(name = "last_used_ip")
    public String lastUsedIp;

    @Column(name = "last_used_user_agent")
    public String lastUsedUserAgent;

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
