CREATE TABLE lyric_alignment_jobs_v17 (
    id TEXT PRIMARY KEY,
    task_type VARCHAR(64) NOT NULL DEFAULT 'LYRICS_ALIGNMENT',
    song_id BIGINT NOT NULL,
    lyric_id BIGINT,
    status VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    import_status VARCHAR(32) NOT NULL,
    audio_relative_path TEXT NOT NULL,
    worker_audio_path TEXT NOT NULL,
    trusted_lyrics_hash VARCHAR(64),
    trusted_lyrics_snapshot TEXT,
    request_snapshot_json TEXT NOT NULL,
    job_dir TEXT NOT NULL,
    error_message TEXT,
    result_summary_json TEXT,
    worker_outcome VARCHAR(32),
    worker_status_json TEXT,
    alignment_json_hash VARCHAR(64),
    lrc_hash VARCHAR(64),
    swlrc_hash VARCHAR(64),
    report_hash VARCHAR(64),
    result_available BOOLEAN NOT NULL DEFAULT FALSE,
    sync_message TEXT,
    created_by TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    queued_at DATETIME,
    started_at DATETIME,
    completed_at DATETIME,
    failed_at DATETIME,
    reviewed_by TEXT,
    reviewed_at DATETIME,
    review_note TEXT,
    imported_by TEXT,
    imported_at DATETIME,
    import_error_message TEXT,
    imported_lyric_id BIGINT,
    FOREIGN KEY (song_id) REFERENCES track_files(id),
    FOREIGN KEY (lyric_id) REFERENCES lyrics(id)
);

INSERT INTO lyric_alignment_jobs_v17 (
    id,
    task_type,
    song_id,
    lyric_id,
    status,
    review_status,
    import_status,
    audio_relative_path,
    worker_audio_path,
    trusted_lyrics_hash,
    trusted_lyrics_snapshot,
    request_snapshot_json,
    job_dir,
    error_message,
    result_summary_json,
    worker_outcome,
    worker_status_json,
    alignment_json_hash,
    lrc_hash,
    swlrc_hash,
    report_hash,
    result_available,
    sync_message,
    created_by,
    created_at,
    updated_at,
    queued_at,
    started_at,
    completed_at,
    failed_at,
    reviewed_by,
    reviewed_at,
    review_note,
    imported_by,
    imported_at,
    import_error_message,
    imported_lyric_id
)
SELECT
    id,
    'LYRICS_ALIGNMENT',
    song_id,
    lyric_id,
    status,
    review_status,
    import_status,
    audio_relative_path,
    worker_audio_path,
    trusted_lyrics_hash,
    trusted_lyrics_snapshot,
    request_snapshot_json,
    job_dir,
    error_message,
    result_summary_json,
    worker_outcome,
    worker_status_json,
    alignment_json_hash,
    lrc_hash,
    swlrc_hash,
    report_hash,
    result_available,
    sync_message,
    created_by,
    created_at,
    updated_at,
    queued_at,
    started_at,
    completed_at,
    failed_at,
    reviewed_by,
    reviewed_at,
    review_note,
    imported_by,
    imported_at,
    import_error_message,
    imported_lyric_id
FROM lyric_alignment_jobs;

DROP TABLE lyric_alignment_jobs;
ALTER TABLE lyric_alignment_jobs_v17 RENAME TO lyric_alignment_jobs;

CREATE INDEX idx_lyric_alignment_jobs_song_id
    ON lyric_alignment_jobs(song_id);

CREATE INDEX idx_lyric_alignment_jobs_status
    ON lyric_alignment_jobs(status);

CREATE INDEX idx_lyric_alignment_jobs_task_type
    ON lyric_alignment_jobs(task_type);

CREATE INDEX idx_lyric_alignment_jobs_created_at
    ON lyric_alignment_jobs(created_at);

CREATE INDEX idx_lyric_alignment_jobs_worker_outcome
    ON lyric_alignment_jobs(worker_outcome);

ALTER TABLE lyrics ADD COLUMN source_draft_id BIGINT;
ALTER TABLE lyrics ADD COLUMN source_text_hash VARCHAR(64);

CREATE INDEX idx_lyrics_source_draft_id
    ON lyrics(source_draft_id);

CREATE TABLE lyric_drafts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    job_id TEXT NOT NULL UNIQUE,
    music_id BIGINT NOT NULL,
    original_text TEXT NOT NULL,
    original_text_hash VARCHAR(64) NOT NULL,
    editable_text TEXT NOT NULL,
    editable_text_hash VARCHAR(64) NOT NULL,
    draft_status VARCHAR(32) NOT NULL,
    report_summary_json TEXT,
    transcript_raw_hash VARCHAR(64),
    transcript_segments_hash VARCHAR(64),
    report_hash VARCHAR(64),
    confirmed_trusted_lyrics_id BIGINT,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    edited_by TEXT,
    edited_at DATETIME,
    confirmed_by TEXT,
    confirmed_at DATETIME,
    rejected_by TEXT,
    rejected_at DATETIME,
    reject_note TEXT,
    error_message TEXT,
    FOREIGN KEY (job_id) REFERENCES lyric_alignment_jobs(id),
    FOREIGN KEY (music_id) REFERENCES track_files(id),
    FOREIGN KEY (confirmed_trusted_lyrics_id) REFERENCES lyrics(id)
);

CREATE INDEX idx_lyric_drafts_music_id
    ON lyric_drafts(music_id);

CREATE INDEX idx_lyric_drafts_draft_status
    ON lyric_drafts(draft_status);

CREATE INDEX idx_lyric_drafts_created_at
    ON lyric_drafts(created_at);
