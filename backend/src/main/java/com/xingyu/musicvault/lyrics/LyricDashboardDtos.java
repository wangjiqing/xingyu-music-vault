package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.library.MusicDtos.MusicResponse;

import java.util.List;

public final class LyricDashboardDtos {
    private LyricDashboardDtos() {
    }

    public record LyricOverviewResponse(
            long totalSongs,
            long songsWithLyrics,
            double lyricsCoverageRate,
            long songsWithSwlrc,
            double swlrcCoverageRate,
            long songsWithLrcOnly,
            long songsWithoutLyrics,
            long alignmentRunning,
            long draftPending
    ) {
    }

    public record DailyRecommendationResponse(List<DailyRecommendationItem> items) {
    }

    public record DailyRecommendationItem(
            Long id,
            String recommendationDate,
            int slotNo,
            String recommendationType,
            String actionStatus,
            MusicResponse music
    ) {
    }

    public record RandomRecommendationRequest(Integer count) {
    }

    public record RandomRecommendationResponse(List<MusicResponse> items, String message) {
    }
}
