CREATE UNIQUE INDEX IF NOT EXISTS idx_lyric_daily_recommendation_active_slot
    ON lyric_daily_recommendation(recommendation_date, slot_no)
    WHERE action_status IN ('PENDING', 'STARTED');
