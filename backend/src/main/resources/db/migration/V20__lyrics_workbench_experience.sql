ALTER TABLE lyric_drafts ADD COLUMN source_type VARCHAR(32) NOT NULL DEFAULT 'WORKER_EXTRACTION';
ALTER TABLE lyric_drafts ADD COLUMN source_metadata_json TEXT;

CREATE INDEX idx_lyric_drafts_source_type
    ON lyric_drafts(source_type);

CREATE TABLE app_settings (
    setting_key VARCHAR(128) PRIMARY KEY,
    setting_value_encrypted TEXT,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_by TEXT,
    updated_at DATETIME NOT NULL,
    last_error TEXT,
    last_checked_at DATETIME
);

CREATE TABLE lyric_draft_sources (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    draft_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    query TEXT NOT NULL,
    title TEXT NOT NULL,
    url TEXT NOT NULL,
    domain TEXT NOT NULL,
    selected_by TEXT NOT NULL,
    selected_at DATETIME NOT NULL,
    FOREIGN KEY (draft_id) REFERENCES lyric_drafts(id) ON DELETE CASCADE
);

CREATE INDEX idx_lyric_draft_sources_draft_id
    ON lyric_draft_sources(draft_id);
