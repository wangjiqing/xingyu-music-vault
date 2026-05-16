package com.xingyu.musicvault.artwork;

import java.time.LocalDateTime;
import java.util.List;

public final class ArtworkDtos {
    private ArtworkDtos() {
    }

    public record ArtworkScanRequest(String path) {
    }

    public record ArtworkScanResponse(
            String path,
            int totalFiles,
            int imported,
            int duplicateFiles,
            int autoBound,
            int unmatched,
            int failed
    ) {
    }

    public record ArtworkBindRequest(Long artworkId) {
    }

    public record ArtworkResponse(
            Long id,
            String fileName,
            String fileExt,
            String mimeType,
            long fileSize,
            Integer width,
            Integer height,
            String hash,
            String sourceType,
            String sourcePath,
            String title,
            String description,
            String previewUrl,
            long boundCount,
            List<BoundTrackResponse> boundTracks,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        public static ArtworkResponse from(Artwork artwork) {
            return from(artwork, 0, List.of());
        }

        public static ArtworkResponse from(Artwork artwork, long boundCount, List<BoundTrackResponse> boundTracks) {
            return new ArtworkResponse(
                    artwork.id,
                    artwork.fileName,
                    artwork.fileExt,
                    artwork.mimeType,
                    artwork.fileSize,
                    artwork.width,
                    artwork.height,
                    artwork.hash,
                    artwork.sourceType,
                    artwork.sourcePath,
                    artwork.title,
                    artwork.description,
                    "/api/artworks/" + artwork.id + "/file",
                    boundCount,
                    boundTracks == null ? List.of() : boundTracks,
                    artwork.createdAt,
                    artwork.updatedAt
            );
        }
    }

    public record BoundTrackResponse(
            Long musicId,
            Long trackId,
            String fileName,
            String filePath,
            String title,
            String artist
    ) {
    }

    public record MusicArtworkResponse(
            Long musicId,
            String artworkStatus,
            Long artworkId,
            String artworkPreviewUrl,
            String artworkFileName
    ) {
        public static MusicArtworkResponse missing(Long musicId) {
            return new MusicArtworkResponse(musicId, "MISSING", null, null, null);
        }
    }
}
