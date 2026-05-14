package com.xingyu.musicvault.lyrics;

import java.time.LocalDateTime;

public final class LyricDtos {
    private LyricDtos() {
    }

    public record LyricScanRequest(
            String path,
            Boolean overwritePrimary
    ) {
    }

    public record LyricScanResponse(
            String path,
            int totalFiles,
            int imported,
            int duplicateFiles,
            int matched,
            int unmatched,
            int skippedBindings,
            int failed
    ) {
    }

    public record SongLyricResponse(
            Long songId,
            String lyricStatus,
            Long lyricId,
            String title,
            String artist,
            String album,
            String language,
            Integer releaseYear,
            String sourceType,
            String sourcePath,
            String format,
            String parseStatus,
            String parseMessage,
            String content,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static SongLyricResponse noLyric(Long songId) {
            return new SongLyricResponse(
                    songId,
                    LyricStatus.NO_LYRIC.name(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        public static SongLyricResponse from(Long songId, String lyricStatus, Lyric lyric) {
            return new SongLyricResponse(
                    songId,
                    lyricStatus,
                    lyric.id,
                    lyric.title,
                    lyric.artist,
                    lyric.album,
                    lyric.language,
                    lyric.releaseYear,
                    lyric.sourceType,
                    lyric.sourcePath,
                    lyric.format,
                    lyric.parseStatus,
                    lyric.parseMessage,
                    lyric.content,
                    lyric.createdAt,
                    lyric.updatedAt
            );
        }
    }
}
