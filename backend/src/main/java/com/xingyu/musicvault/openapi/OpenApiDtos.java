package com.xingyu.musicvault.openapi;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class OpenApiDtos {
    private OpenApiDtos() {
    }

    public record ServerInfoResponse(
            String serviceName,
            String serviceVersion,
            String apiVersion,
            boolean readOnly,
            Map<String, Boolean> features
    ) {
    }

    public record SyncStateResponse(
            long libraryVersion,
            long trackCount,
            long artistCount,
            long albumCount,
            long lyricsCount,
            long artworkCount,
            LocalDateTime lastUpdatedAt,
            String lastChangedAt,
            boolean changesAvailable
    ) {
    }

    public record SyncChangesResponse(
            long fromVersion,
            long toVersion,
            boolean hasMore,
            List<SyncChangeItemResponse> items
    ) {
    }

    public record SyncChangeItemResponse(
            long version,
            String entityType,
            Long entityId,
            String changeType,
            List<String> changedFields,
            String updatedAt
    ) {
    }

    public record OpenPageResponse<T>(
            List<T> items,
            int page,
            int pageSize,
            long total
    ) {
    }

    public record OpenTrackResponse(
            Long id,
            String title,
            String artist,
            String album,
            String albumArtist,
            Long durationMs,
            Integer year,
            Integer trackNo,
            String genre,
            String metadataStatus,
            String lyricsStatus,
            String artworkStatus,
            String fileName,
            String fileExtension,
            long fileSize,
            boolean lyricsAvailable,
            Long lyricId,
            boolean artworkAvailable,
            Long artworkId,
            String artworkUrl,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
    }

    public record LyricsResponse(
            Long trackId,
            Long lyricId,
            String format,
            String content,
            String hash,
            LocalDateTime updatedAt
    ) {
    }

    public record LyricsMetaResponse(
            Long trackId,
            boolean available,
            Long lyricId,
            String format,
            String hash,
            String etag,
            LocalDateTime updatedAt,
            boolean wordLyricsAvailable,
            String wordLyricsUrl,
            String lyricsVersionSource
    ) {
    }

    public record ArtworkMetaResponse(
            Long trackId,
            boolean available,
            Long artworkId,
            String mimeType,
            Long fileSize,
            Integer width,
            Integer height,
            String hash,
            String etag,
            LocalDateTime updatedAt
    ) {
    }

    public record ArtistResponse(
            String artistName,
            long trackCount,
            long albumCount,
            long lyricsCount,
            long artworkCount
    ) {
    }

    public record AlbumResponse(
            String album,
            String albumArtist,
            Integer year,
            long trackCount,
            long lyricsCount,
            long artworkCount
    ) {
    }

    public record MatchTrackResponse(
            boolean matched,
            int score,
            String reason,
            OpenTrackResponse track
    ) {
    }
}
