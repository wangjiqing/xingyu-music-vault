ALTER TABLE lyric_alignment_jobs ADD COLUMN reviewed_by TEXT;
ALTER TABLE lyric_alignment_jobs ADD COLUMN reviewed_at DATETIME;
ALTER TABLE lyric_alignment_jobs ADD COLUMN review_note TEXT;
ALTER TABLE lyric_alignment_jobs ADD COLUMN imported_by TEXT;
ALTER TABLE lyric_alignment_jobs ADD COLUMN imported_at DATETIME;
ALTER TABLE lyric_alignment_jobs ADD COLUMN import_error_message TEXT;
ALTER TABLE lyric_alignment_jobs ADD COLUMN imported_lyric_id BIGINT;

ALTER TABLE lyrics ADD COLUMN source_task_id TEXT;
ALTER TABLE lyrics ADD COLUMN parent_lyrics_id BIGINT;
ALTER TABLE lyrics ADD COLUMN swlrc_path TEXT;
ALTER TABLE lyrics ADD COLUMN swlrc_hash VARCHAR(64);
ALTER TABLE lyrics ADD COLUMN confirmed_at DATETIME;
ALTER TABLE lyrics ADD COLUMN confirmed_by TEXT;

DROP INDEX IF EXISTS idx_lyrics_content_hash;

CREATE INDEX idx_lyrics_content_hash
    ON lyrics(content_hash);

CREATE INDEX idx_lyrics_source_task_id
    ON lyrics(source_task_id);

CREATE UNIQUE INDEX idx_lyrics_alignment_source_task_id
    ON lyrics(source_task_id)
    WHERE source_task_id IS NOT NULL;

CREATE INDEX idx_lyrics_parent_lyrics_id
    ON lyrics(parent_lyrics_id);

CREATE TABLE lyric_alignment_job_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id TEXT NOT NULL,
    music_id BIGINT NOT NULL,
    action VARCHAR(32) NOT NULL,
    operator TEXT NOT NULL,
    note TEXT,
    before_status TEXT,
    after_status TEXT,
    error_message TEXT,
    created_at DATETIME NOT NULL,
    FOREIGN KEY (task_id) REFERENCES lyric_alignment_jobs(id)
);

CREATE INDEX idx_lyric_alignment_job_events_task_id
    ON lyric_alignment_job_events(task_id);

CREATE INDEX idx_lyric_alignment_job_events_action
    ON lyric_alignment_job_events(action);

CREATE INDEX idx_lyric_alignment_job_events_created_at
    ON lyric_alignment_job_events(created_at);
