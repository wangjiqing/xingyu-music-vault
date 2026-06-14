package com.xingyu.musicvault.workbench;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.common.ErrorResponse;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.library.MusicDtos.MusicResponse;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.openapi.OpenApiPreviewService;
import com.xingyu.musicvault.workbench.MusicWorkbenchDtos.MusicWorkbenchResponse;
import com.xingyu.musicvault.workbench.MusicWorkbenchDtos.WorkbenchArtworkResponse;
import com.xingyu.musicvault.workbench.MusicWorkbenchDtos.WorkbenchLyricResponse;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Path("/api/admin/music")
public class MusicWorkbenchResource {
    private static final String ACTIVE = "active";
    private static final String RANGE = "Range";
    private static final String ACCEPT_RANGES = "Accept-Ranges";
    private static final String CONTENT_RANGE = "Content-Range";

    @Inject
    MusicVaultConfig config;

    @Inject
    OpenApiPreviewService openApiPreviewService;

    @GET
    @Path("/{id}/workbench")
    @Produces(MediaType.APPLICATION_JSON)
    public MusicWorkbenchResponse workbench(@PathParam("id") Long id) {
        TrackFile trackFile = findActiveTrackFile(id);
        Track track = trackOf(trackFile);
        Lyric lyric = openApiPreviewService.primaryLyric(id);
        Artwork artwork = openApiPreviewService.primaryArtwork(id);
        Artwork availableArtwork = artworkAvailable(artwork) ? artwork : null;
        return new MusicWorkbenchResponse(
                MusicResponse.from(
                        trackFile,
                        track,
                        lyric == null ? "NO_LYRIC" : "BOUND",
                        lyric == null ? null : lyric.id,
                        availableArtwork == null ? "MISSING" : "BOUND",
                        availableArtwork == null ? null : availableArtwork.id,
                        availableArtwork == null ? null : "/api/artworks/" + availableArtwork.id + "/file",
                        availableArtwork == null ? null : availableArtwork.fileName,
                        availableArtwork != null
                ),
                lyricResponse(lyric),
                artworkResponse(availableArtwork),
                openApiPreviewService.preview(trackFile, track)
        );
    }

    @GET
    @Path("/{id}/openapi-preview")
    @Produces(MediaType.APPLICATION_JSON)
    public OpenApiPreviewService.OpenApiPreview openApiPreview(@PathParam("id") Long id) {
        TrackFile trackFile = findActiveTrackFile(id);
        return openApiPreviewService.preview(trackFile, trackOf(trackFile));
    }

    @GET
    @Path("/{id}/audio")
    @Produces({ "audio/mpeg", "audio/flac", "audio/wav", "audio/ogg", "audio/mp4", MediaType.APPLICATION_OCTET_STREAM })
    public Response audio(@PathParam("id") Long id, @HeaderParam(RANGE) String rangeHeader) {
        TrackFile trackFile = findActiveTrackFile(id);
        java.nio.file.Path path = safeMusicPath(trackFile);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("not_found", "音频文件不存在或不可读取"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        try {
            long size = Files.size(path);
            Range range = parseRange(rangeHeader, size);
            Response.ResponseBuilder builder = range.partial()
                    ? Response.status(Response.Status.PARTIAL_CONTENT).entity(stream(path, range.start(), range.length()))
                    : Response.ok(stream(path, 0, size));
            if (range.partial()) {
                builder.header(CONTENT_RANGE, "bytes " + range.start() + "-" + range.end() + "/" + size);
            }
            return builder
                    .type(contentType(trackFile.fileExt))
                    .header(ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_LENGTH, range.length())
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sanitizeFileName(trackFile.fileName) + "\"")
                    .build();
        } catch (IOException exception) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse("not_found", "音频文件不存在或不可读取"))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    private WorkbenchLyricResponse lyricResponse(Lyric lyric) {
        if (lyric == null) {
            return new WorkbenchLyricResponse(false, null, null, null, null);
        }
        return new WorkbenchLyricResponse(true, lyric.id, lyric.format, lyric.content, lyric.updatedAt);
    }

    private WorkbenchArtworkResponse artworkResponse(Artwork artwork) {
        if (artwork == null) {
            return new WorkbenchArtworkResponse(false, null, null, null, null, null, null, null, null);
        }
        return new WorkbenchArtworkResponse(
                true,
                artwork.id,
                artwork.mimeType,
                artwork.fileName,
                artwork.fileSize,
                artwork.width,
                artwork.height,
                "/api/artworks/" + artwork.id + "/file",
                artwork.updatedAt
        );
    }

    private TrackFile findActiveTrackFile(Long id) {
        TrackFile trackFile = TrackFile.findById(id);
        if (trackFile == null || !(trackFile.deleteStatus == null || ACTIVE.equals(trackFile.deleteStatus))) {
            throw new NotFoundException("Music not found");
        }
        return trackFile;
    }

    private Track trackOf(TrackFile trackFile) {
        return trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
    }

    private boolean artworkAvailable(Artwork artwork) {
        return artwork != null
                && hasText(artwork.filePath)
                && Files.isRegularFile(java.nio.file.Path.of(artwork.filePath))
                && Files.isReadable(java.nio.file.Path.of(artwork.filePath));
    }

    private java.nio.file.Path safeMusicPath(TrackFile trackFile) {
        java.nio.file.Path path = java.nio.file.Path.of(trackFile.filePath).toAbsolutePath().normalize();
        java.nio.file.Path realPath;
        try {
            realPath = path.toRealPath();
        } catch (IOException exception) {
            throw new NotFoundException("Music file not found");
        }
        if (allowedMusicRoots().stream().noneMatch(root -> realPath.startsWith(root) && !realPath.equals(root))) {
            throw new NotFoundException("Music file not found");
        }
        return realPath;
    }

    private List<java.nio.file.Path> allowedMusicRoots() {
        if (config.musicDirs() == null) {
            return List.of();
        }
        return config.musicDirs().stream()
                .filter(MusicWorkbenchResource::hasText)
                .map(java.nio.file.Path::of)
                .map(path -> path.toAbsolutePath().normalize())
                .map(this::existingRealPath)
                .filter(Objects::nonNull)
                .toList();
    }

    private java.nio.file.Path existingRealPath(java.nio.file.Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return null;
        }
    }

    private Range parseRange(String rangeHeader, long size) {
        if (!hasText(rangeHeader) || !rangeHeader.startsWith("bytes=")) {
            return new Range(0, Math.max(size - 1, 0), size, false);
        }
        String value = rangeHeader.substring("bytes=".length()).trim();
        int dash = value.indexOf('-');
        if (dash < 0) {
            throw new WebApplicationException(Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(CONTENT_RANGE, "bytes */" + size)
                    .build());
        }
        long start = dash == 0 ? Math.max(size - Long.parseLong(value.substring(1)), 0) : Long.parseLong(value.substring(0, dash));
        long end = dash == value.length() - 1 ? size - 1 : Long.parseLong(value.substring(dash + 1));
        if (start < 0 || end < start || start >= size) {
            throw new WebApplicationException(Response.status(Response.Status.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(CONTENT_RANGE, "bytes */" + size)
                    .build());
        }
        long normalizedEnd = Math.min(end, size - 1);
        return new Range(start, normalizedEnd, size, !(start == 0 && normalizedEnd == size - 1));
    }

    private StreamingOutput stream(java.nio.file.Path path, long start, long length) {
        return output -> copyRange(path, output, start, length);
    }

    private void copyRange(java.nio.file.Path path, OutputStream output, long start, long length) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = length;
        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            file.seek(start);
            while (remaining > 0) {
                int read = file.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private String contentType(String extension) {
        String normalized = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "mp3" -> "audio/mpeg";
            case "flac" -> "audio/flac";
            case "wav" -> "audio/wav";
            case "ogg", "oga" -> "audio/ogg";
            case "m4a", "aac" -> "audio/mp4";
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "audio";
        }
        String sanitized = fileName.replaceAll("[\\r\\n\";\\\\]", "").trim();
        return sanitized.isBlank() ? "audio" : sanitized;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record Range(long start, long end, long total, boolean partial) {
        long length() {
            return total == 0 ? 0 : end - start + 1;
        }
    }
}
