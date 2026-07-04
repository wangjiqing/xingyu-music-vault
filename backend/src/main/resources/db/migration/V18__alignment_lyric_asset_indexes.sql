CREATE INDEX IF NOT EXISTS idx_lyrics_source_type_source_path
    ON lyrics(source_type, source_path);

CREATE INDEX IF NOT EXISTS idx_lyrics_source_type_task
    ON lyrics(source_type, source_task_id);
