package com.xingyu.musicvault.library;

import java.time.LocalDateTime;

public final class TrackFileDtos {
    private TrackFileDtos() {
    }

    public record TrackFileResponse(
            Long id,
            Long trackId,
            String filePath,
            String fileName,
            String fileExt,
            long fileSize,
            LocalDateTime lastModifiedAt,
            Long scanJobId,
            LocalDateTime deletedAt,
            String trashPath,
            String deleteStatus,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static TrackFileResponse from(TrackFile trackFile) {
            return new TrackFileResponse(
                    trackFile.id,
                    trackFile.trackId,
                    trackFile.filePath,
                    trackFile.fileName,
                    trackFile.fileExt,
                    trackFile.fileSize,
                    trackFile.lastModifiedAt,
                    trackFile.scanJobId,
                    trackFile.deletedAt,
                    trackFile.trashPath,
                    trackFile.deleteStatus,
                    trackFile.createdAt,
                    trackFile.updatedAt
            );
        }
    }
}
