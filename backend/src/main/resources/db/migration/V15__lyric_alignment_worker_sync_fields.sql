ALTER TABLE lyric_alignment_jobs ADD COLUMN worker_outcome VARCHAR(32);
ALTER TABLE lyric_alignment_jobs ADD COLUMN worker_status_json TEXT;
ALTER TABLE lyric_alignment_jobs ADD COLUMN alignment_json_hash VARCHAR(64);
ALTER TABLE lyric_alignment_jobs ADD COLUMN lrc_hash VARCHAR(64);
ALTER TABLE lyric_alignment_jobs ADD COLUMN swlrc_hash VARCHAR(64);
ALTER TABLE lyric_alignment_jobs ADD COLUMN report_hash VARCHAR(64);
ALTER TABLE lyric_alignment_jobs ADD COLUMN result_available BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lyric_alignment_jobs ADD COLUMN sync_message TEXT;

CREATE INDEX idx_lyric_alignment_jobs_worker_outcome
    ON lyric_alignment_jobs(worker_outcome);
