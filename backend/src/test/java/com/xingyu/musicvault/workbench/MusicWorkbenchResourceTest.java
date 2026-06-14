package com.xingyu.musicvault.workbench;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class MusicWorkbenchResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ALLOWED_MUSIC_ROOT = Path.of("target/test-music");

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ALLOWED_MUSIC_ROOT);
        musicDir = Files.createTempDirectory(ALLOWED_MUSIC_ROOT, "workbench-test-");
        MusicArtworkBinding.deleteAll();
        Artwork.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void unauthenticatedWorkbenchRequestIsRejected() {
        given()
                .when()
                .get("/api/admin/music/1/workbench")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void authenticatedWorkbenchReturnsMusicLyricsArtworkAndPreview() throws IOException {
        Long musicId = createMusicWithFile("preview.flac", "Preview", "Tester", "audio-data");
        Long lyricId = bindLyric(musicId, "第一句\n第二句");
        Long artworkId = bindArtwork(musicId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{id}/workbench", musicId)
                .then()
                .statusCode(200)
                .body("music.id", equalTo(musicId.intValue()))
                .body("music.title", equalTo("Preview"))
                .body("lyrics.available", equalTo(true))
                .body("lyrics.lyricId", equalTo(lyricId.intValue()))
                .body("lyrics.content", containsString("第一句"))
                .body("artwork.available", equalTo(true))
                .body("artwork.artworkId", equalTo(artworkId.intValue()))
                .body("openApiPreview.track.id", equalTo(musicId.intValue()))
                .body("openApiPreview.track.lyricsAvailable", equalTo(true))
                .body("openApiPreview.track.artworkAvailable", equalTo(true))
                .body("openApiPreview.resourceUrls.track", equalTo("/api/open/v1/tracks/" + musicId));
    }

    @Test
    void workbenchReturnsEmptyStatesForMissingLyricsAndArtwork() throws IOException {
        Long musicId = createMusicWithFile("empty.flac", "Empty", "Tester", "audio");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{id}/workbench", musicId)
                .then()
                .statusCode(200)
                .body("lyrics.available", equalTo(false))
                .body("lyrics.content", nullValue())
                .body("artwork.available", equalTo(false))
                .body("artwork.previewUrl", nullValue())
                .body("openApiPreview.track.lyricsAvailable", equalTo(false))
                .body("openApiPreview.track.artworkAvailable", equalTo(false));
    }

    @Test
    void audioStreamsOnlyRegisteredMusicFileAndSupportsRange() throws IOException {
        Long musicId = createMusicWithFile("stream.mp3", "Stream", "Tester", "0123456789");

        given()
                .header("Authorization", AUTHORIZATION)
                .header("Range", "bytes=2-5")
                .when()
                .get("/api/admin/music/{id}/audio", musicId)
                .then()
                .statusCode(206)
                .header("Accept-Ranges", equalTo("bytes"))
                .header("Content-Range", equalTo("bytes 2-5/10"))
                .header("Content-Length", equalTo("4"))
                .contentType("audio/mpeg");

        given()
                .header("Authorization", AUTHORIZATION)
                .header("Range", "bytes=0-")
                .when()
                .get("/api/admin/music/{id}/audio", musicId)
                .then()
                .statusCode(200)
                .header("Accept-Ranges", equalTo("bytes"))
                .header("Content-Length", equalTo("10"))
                .contentType("audio/mpeg");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/999999/audio")
                .then()
                .statusCode(404);
    }

    @Test
    void audioReturnsNotFoundWhenRegisteredFileIsMissing() {
        Long musicId = createMusic("missing.flac", "Missing", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{id}/audio", musicId)
                .then()
                .statusCode(404);
    }

    @Test
    void audioDoesNotReadArbitraryPathOutsideMusicRoots() throws IOException {
        Path outside = Files.createTempFile("outside-workbench", ".flac");
        Files.writeString(outside, "secret");
        Long musicId = createMusicRecordForPath(outside, "Outside", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{id}/audio", musicId)
                .then()
                .statusCode(404);
    }

    @Test
    void openApiPreviewReturnsStableStructure() throws IOException {
        Long musicId = createMusicWithFile("openapi.flac", "Open API", "Tester", "audio");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{id}/openapi-preview", musicId)
                .then()
                .statusCode(200)
                .body("track.id", equalTo(musicId.intValue()))
                .body("lyrics.available", equalTo(false))
                .body("artwork.available", equalTo(false))
                .body("resourceUrls.track", equalTo("/api/open/v1/tracks/" + musicId))
                .body("resourceUrls.lyricsMeta", notNullValue())
                .body("resourceUrls.artworkMeta", notNullValue());
    }

    private Long createMusicWithFile(String fileName, String title, String artist, String content) throws IOException {
        Files.writeString(musicDir.resolve(fileName), content);
        return createMusic(fileName, title, artist);
    }

    private Long createMusic(String fileName, String title, String artist) {
        return createMusicRecordForPath(musicDir.resolve(fileName), title, artist);
    }

    private Long createMusicRecordForPath(Path path, String title, String artist) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.metadataStatus = "synced";
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = path.toAbsolutePath().normalize().toString();
            trackFile.fileName = path.getFileName().toString();
            trackFile.fileExt = extension(path.getFileName().toString());
            trackFile.fileSize = Files.exists(path) ? fileSize(path) : 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private Long bindLyric(Long musicId, String content) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Lyric lyric = new Lyric();
            lyric.title = "Preview";
            lyric.artist = "Tester";
            lyric.sourceType = "local";
            lyric.content = content;
            lyric.contentHash = "lyrics-hash";
            lyric.format = "lrc";
            lyric.parseStatus = "parsed";
            lyric.persist();

            SongLyric binding = new SongLyric();
            binding.songId = musicId;
            binding.lyricId = lyric.id;
            binding.matchType = "manual";
            binding.matchScore = 100;
            binding.isPrimary = true;
            binding.persist();
            return lyric.id;
        });
    }

    private Long bindArtwork(Long musicId) throws IOException {
        Path artworkPath = musicDir.resolve("cover.jpg");
        Files.writeString(artworkPath, "image");
        return QuarkusTransaction.requiringNew().call(() -> {
            Artwork artwork = new Artwork();
            artwork.filePath = artworkPath.toAbsolutePath().normalize().toString();
            artwork.fileName = "cover.jpg";
            artwork.fileExt = "jpg";
            artwork.mimeType = "image/jpeg";
            artwork.fileSize = 5;
            artwork.hash = "artwork-hash";
            artwork.sourceType = "local";
            artwork.persist();

            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = musicId;
            binding.artworkId = artwork.id;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
            return artwork.id;
        });
    }

    private long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return 1;
        }
    }

    private String extension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? "" : fileName.substring(dotIndex + 1);
    }
}
