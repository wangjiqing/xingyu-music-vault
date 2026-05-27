CREATE TABLE openapi_library_state (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    library_version INTEGER NOT NULL DEFAULT 1,
    last_changed_at TEXT
);

INSERT INTO openapi_library_state (id, library_version, last_changed_at)
VALUES (1, 1, NULL);

CREATE TABLE openapi_sync_change_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    version INTEGER NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id INTEGER NOT NULL,
    change_type TEXT NOT NULL,
    changed_fields_json TEXT,
    changed_at TEXT NOT NULL
);

CREATE UNIQUE INDEX idx_openapi_sync_change_log_version
ON openapi_sync_change_log(version);

CREATE INDEX idx_openapi_sync_change_log_entity
ON openapi_sync_change_log(entity_type, entity_id);

CREATE INDEX idx_openapi_sync_change_log_changed_at
ON openapi_sync_change_log(changed_at);
