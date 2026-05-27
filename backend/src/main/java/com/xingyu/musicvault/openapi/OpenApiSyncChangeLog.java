package com.xingyu.musicvault.openapi;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "openapi_sync_change_log")
public class OpenApiSyncChangeLog extends PanacheEntityBase {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(columnDefinition = "integer")
    public Long id;

    @Column(nullable = false)
    public long version;

    @Column(name = "entity_type", nullable = false)
    public String entityType;

    @Column(name = "entity_id", nullable = false)
    public Long entityId;

    @Column(name = "change_type", nullable = false)
    public String changeType;

    @Column(name = "changed_fields_json", columnDefinition = "text")
    public String changedFieldsJson;

    @Column(name = "changed_at", nullable = false)
    public String changedAt;
}
