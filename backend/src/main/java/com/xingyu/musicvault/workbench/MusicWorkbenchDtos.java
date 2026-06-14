package com.xingyu.musicvault.workbench;

import com.xingyu.musicvault.library.MusicDtos.MusicResponse;
import com.xingyu.musicvault.openapi.OpenApiPreviewService.OpenApiPreview;

import java.time.LocalDateTime;

public final class MusicWorkbenchDtos {
    private MusicWorkbenchDtos() {
    }

    public record MusicWorkbenchResponse(
            MusicResponse music,
            WorkbenchLyricResponse lyrics,
            WorkbenchArtworkResponse artwork,
            OpenApiPreview openApiPreview
    ) {
    }

    public record WorkbenchLyricResponse(
            boolean available,
            Long lyricId,
            String format,
            String content,
            LocalDateTime updatedAt
    ) {
    }

    public record WorkbenchArtworkResponse(
            boolean available,
            Long artworkId,
            String mimeType,
            String fileName,
            Long fileSize,
            Integer width,
            Integer height,
            String previewUrl,
            LocalDateTime updatedAt
    ) {
    }
}
