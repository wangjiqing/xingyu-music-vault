CREATE TABLE lyric_alignment_jobs (
    id TEXT PRIMARY KEY,
    song_id BIGINT NOT NULL,
    lyric_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    review_status VARCHAR(32) NOT NULL,
    import_status VARCHAR(32) NOT NULL,
    audio_relative_path TEXT NOT NULL,
    worker_audio_path TEXT NOT NULL,
    trusted_lyrics_hash VARCHAR(64) NOT NULL,
    trusted_lyrics_snapshot TEXT NOT NULL,
    request_snapshot_json TEXT NOT NULL,
    job_dir TEXT NOT NULL,
    error_message TEXT,
    result_summary_json TEXT,
    created_by TEXT NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    queued_at DATETIME,
    started_at DATETIME,
    completed_at DATETIME,
    failed_at DATETIME,
    FOREIGN KEY (song_id) REFERENCES track_files(id),
    FOREIGN KEY (lyric_id) REFERENCES lyrics(id)
);

CREATE INDEX idx_lyric_alignment_jobs_song_id
    ON lyric_alignment_jobs(song_id);

CREATE INDEX idx_lyric_alignment_jobs_status
    ON lyric_alignment_jobs(status);

CREATE INDEX idx_lyric_alignment_jobs_created_at
    ON lyric_alignment_jobs(created_at);
