package com.xingyu.musicvault.openapi;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "openapi_library_state")
public class OpenApiLibraryState extends PanacheEntityBase {
    @Id
    @Column(columnDefinition = "integer")
    public Integer id;

    @Column(name = "library_version", nullable = false)
    public long libraryVersion;

    @Column(name = "last_changed_at")
    public String lastChangedAt;
}
