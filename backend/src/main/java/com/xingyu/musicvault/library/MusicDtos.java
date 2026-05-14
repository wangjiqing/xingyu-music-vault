package com.xingyu.musicvault.library;

import com.xingyu.musicvault.job.ScanJob;

import java.time.LocalDateTime;

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
            String lyricStatus,
            Long lyricId,
            String filePath,
            String fileName,
            String fileExtension,
            long fileSize,
            LocalDateTime lastModifiedTime,
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

        public static MusicResponse from(TrackFile trackFile, Track track, String lyricStatus, Long lyricId) {
            return new MusicResponse(
                    trackFile.id,
                    titleOf(track, trackFile),
                    artistOf(track),
                    track == null ? null : track.album,
                    track == null ? null : track.albumArtist,
                    track == null ? null : track.duration,
                    lyricStatus,
                    lyricId,
                    trackFile.filePath,
                    trackFile.fileName,
                    trackFile.fileExt,
                    trackFile.fileSize,
                    trackFile.lastModifiedAt,
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
}
