package com.xingyu.musicvault.openapi;

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
@Table(name = "openapi_request_nonces")
public class OpenApiRequestNonce extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(name = "access_key", nullable = false)
    public String accessKey;

    @Column(nullable = false)
    public String nonce;

    @Column(name = "request_timestamp", nullable = false)
    public LocalDateTime requestTimestamp;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    public LocalDateTime expiresAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
    }
}
