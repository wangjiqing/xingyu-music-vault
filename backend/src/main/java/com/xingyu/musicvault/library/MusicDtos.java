package com.xingyu.musicvault.library;

import com.xingyu.musicvault.job.ScanJob;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public final class MusicDtos {
    private MusicDtos() {
    }

    public record MusicScanRequest(String path) {
    }

    public record MusicScanAccepted(
            boolean accepted,
            Long scanJobId,
            String message
    ) {
        public static MusicScanAccepted from(ScanJob scanJob) {
            return new MusicScanAccepted(true, scanJob.id, "Scan accepted");
        }
    }

    public record MusicResponse(
            Long id,
            String title,
            String artist,
            String album,
            String albumArtist,
            Long duration,
            Integer year,
            Integer trackNo,
            String genre,
            LocalDateTime metadataUpdatedAt,
            String lyricStatus,
            Long lyricId,
            String artworkStatus,
            Long artworkId,
            String artworkPreviewUrl,
            String artworkFileName,
            Boolean artworkFileExists,
            String filePath,
            String fileName,
            String fileExtension,
            long fileSize,
            LocalDateTime lastModifiedTime,
            LocalDateTime deletedAt,
            String trashPath,
            String originalPath,
            String deleteStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static MusicResponse from(TrackFile trackFile) {
            Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
            return from(trackFile, track, null, null);
        }

        public static MusicResponse from(TrackFile trackFile, String lyricStatus, Long lyricId) {
            Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
            return from(trackFile, track, lyricStatus, lyricId);
        }

        public static MusicResponse from(
                TrackFile trackFile,
                String lyricStatus,
                Long lyricId,
                String artworkStatus,
                Long artworkId,
                String artworkPreviewUrl,
                String artworkFileName,
                Boolean artworkFileExists
        ) {
            Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
            return from(trackFile, track, lyricStatus, lyricId, artworkStatus, artworkId, artworkPreviewUrl, artworkFileName, artworkFileExists);
        }

        public static MusicResponse from(TrackFile trackFile, Track track, String lyricStatus, Long lyricId) {
            return from(trackFile, track, lyricStatus, lyricId, null, null, null, null, null);
        }

        public static MusicResponse from(
                TrackFile trackFile,
                Track track,
                String lyricStatus,
                Long lyricId,
                String artworkStatus,
                Long artworkId,
                String artworkPreviewUrl,
                String artworkFileName,
                Boolean artworkFileExists
        ) {
            return new MusicResponse(
                    trackFile.id,
                    titleOf(track, trackFile),
                    artistOf(track),
                    track == null ? null : track.album,
                    track == null ? null : track.albumArtist,
                    track == null ? null : track.duration,
                    track == null ? null : track.year,
                    track == null ? null : track.trackNo,
                    track == null ? null : track.genre,
                    track == null ? null : track.metadataUpdatedAt,
                    lyricStatus,
                    lyricId,
                    artworkStatus == null ? "MISSING" : artworkStatus,
                    artworkId,
                    artworkPreviewUrl,
                    artworkFileName,
                    artworkFileExists,
                    trackFile.filePath,
                    trackFile.fileName,
                    trackFile.fileExt,
                    trackFile.fileSize,
                    trackFile.lastModifiedAt,
                    trackFile.deletedAt,
                    trackFile.trashPath,
                    trackFile.originalPath,
                    trackFile.deleteStatus == null ? "active" : trackFile.deleteStatus,
                    trackFile.createdAt,
                    trackFile.updatedAt
            );
        }

        private static String titleOf(Track track, TrackFile trackFile) {
            if (track != null && track.title != null) {
                return track.title;
            }
            int dotIndex = trackFile.fileName.lastIndexOf('.');
            if (dotIndex <= 0) {
                return trackFile.fileName;
            }
            return trackFile.fileName.substring(0, dotIndex);
        }

        private static String artistOf(Track track) {
            if (track == null || track.artist == null || track.artist.isBlank()) {
                return "Unknown";
            }
            return track.artist;
        }
    }

    public record MusicMetadataUpdateRequest(
            String title,
            String artist,
            String album,
            Integer year,
            Integer trackNo,
            String genre
    ) {
    }

    public record MusicMetadataBatchUpdateRequest(
            List<Long> ids,
            String artist,
            String album,
            Integer year,
            String genre
    ) {
    }

    public record MusicMetadataBatchUpdateResponse(long updated) {
    }

    public record MusicFileResponse(
            Long id,
            String filePath,
            String fileName,
            String fileExtension,
            long fileSize,
            LocalDateTime lastModifiedTime,
            LocalDateTime deletedAt,
            String trashPath,
            String originalPath,
            String deleteStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static MusicFileResponse from(TrackFile trackFile) {
            return new MusicFileResponse(
                    trackFile.id,
                    trackFile.filePath,
                    trackFile.fileName,
                    trackFile.fileExt,
                    trackFile.fileSize,
                    trackFile.lastModifiedAt,
                    trackFile.deletedAt,
                    trackFile.trashPath,
                    trackFile.originalPath,
                    trackFile.deleteStatus == null ? "active" : trackFile.deleteStatus,
                    trackFile.createdAt,
                    trackFile.updatedAt
            );
        }
    }

    public record MusicStatsResponse(
            long total,
            long metadataIncomplete,
            long lyricsReady,
            long artworkReady,
            long trashed
    ) {}

    public record ArtistPageResponse(
            List<ArtistResponse> items,
            long total,
            int page,
            int pageSize
    ) {
    }

    public record ArtistResponse(
            String artist,
            String artistKey,
            long trackCount,
            long albumCount,
            long lyricsCount,
            long artworkCount,
            long metadataIncompleteCount
    ) {
    }

    public record MusicTrashResponse(
            Long id,
            String title,
            String artist,
            String album,
            String fileName,
            String originalPath,
            String trashPath,
            LocalDateTime deletedAt,
            boolean trashFileExists,
            String deleteStatus
    ) {
        public static MusicTrashResponse from(TrackFile trackFile, Track track) {
            String originalPath = trackFile.originalPath == null ? trackFile.filePath : trackFile.originalPath;
            return new MusicTrashResponse(
                    trackFile.id,
                    titleOf(track, trackFile),
                    artistOf(track),
                    track == null ? null : track.album,
                    trackFile.fileName,
                    originalPath,
                    trackFile.trashPath,
                    trackFile.deletedAt,
                    // trashPath is written by safeDelete() under a validated root;
                    // the existence check is read-only, so bare Path.of() is acceptable.
                    trackFile.trashPath != null && Files.isRegularFile(Path.of(trackFile.trashPath)),
                    trackFile.deleteStatus == null ? "active" : trackFile.deleteStatus
            );
        }

        private static String titleOf(Track track, TrackFile trackFile) {
            if (track != null && track.title != null) {
                return track.title;
            }
            int dotIndex = trackFile.fileName.lastIndexOf('.');
            if (dotIndex <= 0) {
                return trackFile.fileName;
            }
            return trackFile.fileName.substring(0, dotIndex);
        }

        private static String artistOf(Track track) {
            if (track == null || track.artist == null || track.artist.isBlank()) {
                return "Unknown";
            }
            return track.artist;
        }
    }
}
