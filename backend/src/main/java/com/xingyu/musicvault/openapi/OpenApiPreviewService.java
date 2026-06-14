package com.xingyu.musicvault.openapi;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.artwork.MusicArtworkBindingRepository;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import com.xingyu.musicvault.lyrics.SongLyricRepository;
import com.xingyu.musicvault.openapi.OpenApiDtos.ArtworkMetaResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.LyricsMetaResponse;
import com.xingyu.musicvault.openapi.OpenApiDtos.OpenTrackResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class OpenApiPreviewService {
    private static final String ACTIVE = "active";

    @Inject
    SongLyricRepository songLyricRepository;

    @Inject
    MusicArtworkBindingRepository artworkBindingRepository;

    @Inject
    OpenApiHashService hashService;

    public OpenApiPreview preview(TrackFile trackFile, Track track) {
        Lyric lyric = primaryLyric(trackFile.id);
        Artwork artwork = primaryArtwork(trackFile.id);
        Artwork availableArtwork = artworkAvailable(artwork) ? artwork : null;

        return new OpenApiPreview(
                toTrackResponse(trackFile, track, lyric, availableArtwork),
                toLyricsMeta(trackFile.id, lyric),
                toArtworkMeta(trackFile.id, availableArtwork),
                resourceUrls(trackFile.id, lyric, availableArtwork)
        );
    }

    public OpenTrackResponse toTrackResponse(TrackFile trackFile, Track track, Lyric lyric, Artwork artwork) {
        Artwork availableArtwork = artworkAvailable(artwork) ? artwork : null;
        return new OpenTrackResponse(
                trackFile.id,
                titleOf(track, trackFile),
                track == null ? null : track.artist,
                track == null ? null : track.album,
                track == null ? null : track.albumArtist,
                track == null ? null : track.duration,
                track == null ? null : track.year,
                track == null ? null : track.trackNo,
                track == null ? null : track.genre,
                track == null ? null : track.metadataStatus,
                lyric == null ? "NO_LYRIC" : "BOUND",
                availableArtwork == null ? "MISSING" : "BOUND",
                trackFile.fileName,
                trackFile.fileExt,
                trackFile.fileSize,
                lyric != null,
                lyric == null ? null : lyric.id,
                availableArtwork != null,
                availableArtwork == null ? null : availableArtwork.id,
                availableArtwork == null ? null : "/api/open/v1/tracks/" + trackFile.id + "/artwork",
                trackFile.createdAt,
                max(trackFile.updatedAt, track == null ? null : track.updatedAt)
        );
    }

    public Lyric primaryLyric(Long trackId) {
        SongLyric binding = songLyricRepository.findPrimaryBySongId(trackId);
        return binding == null ? null : Lyric.findById(binding.lyricId);
    }

    public Artwork primaryArtwork(Long trackId) {
        MusicArtworkBinding binding = artworkBindingRepository.findPrimaryTrackCoverByMusicId(trackId);
        return binding == null ? null : Artwork.findById(binding.artworkId);
    }

    private LyricsMetaResponse toLyricsMeta(Long trackId, Lyric lyric) {
        if (lyric == null) {
            return new LyricsMetaResponse(trackId, false, null, null, null, null, null);
        }
        String hash = hashService.lyricsHash(lyric);
        return new LyricsMetaResponse(trackId, true, lyric.id, lyric.format, hash, hashService.lyricsEtag(trackId, hash), lyric.updatedAt);
    }

    private ArtworkMetaResponse toArtworkMeta(Long trackId, Artwork artwork) {
        if (artwork == null) {
            return new ArtworkMetaResponse(trackId, false, null, null, null, null, null, null, null, null);
        }
        String hash = hashService.artworkHash(artwork, java.nio.file.Path.of(artwork.filePath));
        return new ArtworkMetaResponse(
                trackId,
                true,
                artwork.id,
                artwork.mimeType,
                artwork.fileSize,
                artwork.width,
                artwork.height,
                hash,
                hashService.artworkEtag(trackId, hash),
                artwork.updatedAt
        );
    }

    private Map<String, String> resourceUrls(Long trackId, Lyric lyric, Artwork artwork) {
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("track", "/api/open/v1/tracks/" + trackId);
        urls.put("lyricsMeta", "/api/open/v1/tracks/" + trackId + "/lyrics/meta");
        urls.put("artworkMeta", "/api/open/v1/tracks/" + trackId + "/artwork/meta");
        if (lyric != null) {
            urls.put("lyrics", "/api/open/v1/tracks/" + trackId + "/lyrics");
        }
        if (artwork != null) {
            urls.put("artwork", "/api/open/v1/tracks/" + trackId + "/artwork");
        }
        return urls;
    }

    private boolean artworkAvailable(Artwork artwork) {
        return artwork != null
                && hasText(artwork.filePath)
                && Files.isRegularFile(java.nio.file.Path.of(artwork.filePath))
                && Files.isReadable(java.nio.file.Path.of(artwork.filePath));
    }

    private String titleOf(Track track, TrackFile trackFile) {
        if (track != null && hasText(track.title)) {
            return track.title;
        }
        int dotIndex = trackFile.fileName.lastIndexOf('.');
        return dotIndex <= 0 ? trackFile.fileName : trackFile.fileName.substring(0, dotIndex);
    }

    private LocalDateTime max(LocalDateTime left, LocalDateTime right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isAfter(right) ? left : right;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public record OpenApiPreview(
            OpenTrackResponse track,
            LyricsMetaResponse lyrics,
            ArtworkMetaResponse artwork,
            Map<String, String> resourceUrls
    ) {
    }
}
