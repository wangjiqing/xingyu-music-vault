CREATE TABLE lyric_daily_recommendation (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    recommendation_date TEXT NOT NULL,
    slot_no INTEGER NOT NULL,
    music_id INTEGER NOT NULL,
    recommendation_type TEXT NOT NULL,
    action_status TEXT NOT NULL DEFAULT 'PENDING',
    replaced_by_id INTEGER,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    acted_at DATETIME,
    FOREIGN KEY (music_id) REFERENCES track_files(id),
    FOREIGN KEY (replaced_by_id) REFERENCES lyric_daily_recommendation(id)
);

CREATE INDEX idx_lyric_daily_recommendation_date
    ON lyric_daily_recommendation(recommendation_date);

CREATE INDEX idx_lyric_daily_recommendation_music
    ON lyric_daily_recommendation(music_id);

CREATE UNIQUE INDEX idx_lyric_daily_recommendation_date_music
    ON lyric_daily_recommendation(recommendation_date, music_id);
