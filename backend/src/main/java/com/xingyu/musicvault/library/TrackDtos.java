package com.xingyu.musicvault.library;

import java.time.LocalDateTime;

public final class TrackDtos {
    private TrackDtos() {
    }

    public record TrackResponse(
            Long id,
            String title,
            String normalizedTitle,
            String metadataStatus,
            String lyricsStatus,
            String artworkStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static TrackResponse from(Track track) {
            return new TrackResponse(
                    track.id,
                    track.title,
                    track.normalizedTitle,
                    track.metadataStatus,
                    track.lyricsStatus,
                    track.artworkStatus,
                    track.createdAt,
                    track.updatedAt
            );
        }
    }

    public record TrackCreateRequest(
            String title,
            String metadataStatus,
            String lyricsStatus,
            String artworkStatus
    ) {
    }

    public record TrackUpdateRequest(
            String title,
            String metadataStatus,
            String lyricsStatus,
            String artworkStatus
    ) {
    }
}
