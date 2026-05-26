package com.xingyu.musicvault.openapi;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class OpenApiResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ROOT = Path.of("target/test-music/open-api");

    Path workDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ROOT);
        workDir = Files.createTempDirectory(ROOT, "case-");
        MusicArtworkBinding.deleteAll();
        Artwork.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
    }

    @Test
    void serverInfoAndSyncStateExposeReadOnlyCapabilitiesAndCounts() {
        createTrack("晴天.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop", 242_000L);
        createTrack("七里香.flac", "七里香", "周杰伦", "七里香", 2004, "Pop", 280_000L);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(200)
                .body("serviceVersion", equalTo("0.9.0"))
                .body("apiVersion", equalTo("v1"))
                .body("readOnly", equalTo(true))
                .body("features.tracks", equalTo(true))
                .body("features.scanTrigger", equalTo(false));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/sync/state")
                .then()
                .statusCode(200)
                .body("trackCount", equalTo(2))
                .body("artistCount", equalTo(1))
                .body("albumCount", equalTo(2));
    }

    @Test
    void tracksSupportPagingKeywordFiltersAndPathSafeDetail() {
        Long firstId = createTrack("周杰伦 - 晴天.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop", 242_000L);
        createTrack("孙燕姿 - 遇见.flac", "遇见", "孙燕姿", "The Moment", 2003, "Mandopop", 210_000L);
        createTrack("周杰伦 - 七里香.flac", "七里香", "周杰伦", "七里香", 2004, "Pop", 280_000L);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks?page=0&pageSize=2")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("page", equalTo(0))
                .body("pageSize", equalTo(2))
                .body("total", equalTo(3));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("keyword", "晴")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].title", equalTo("晴天"));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("artist", "周杰伦")
                .queryParam("album", "叶惠美")
                .queryParam("year", 2003)
                .queryParam("genre", "Pop")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(firstId.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks?pageSize=101")
                .then()
                .statusCode(400)
                .body("code", equalTo("INVALID_ARGUMENT"))
                .body("traceId", notNullValue())
                .body("details", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}", firstId)
                .then()
                .statusCode(200)
                .body("id", equalTo(firstId.intValue()))
                .body("fileName", equalTo("周杰伦 - 晴天.flac"))
                .body("filePath", nullValue())
                .body("sourcePath", nullValue());
    }

    @Test
    void lyricsArtworkAggregatesAndMatchAreAvailable() throws IOException {
        Long qingtianId = createTrack("周杰伦 - 晴天.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop", 242_000L);
        Long qlxId = createTrack("周杰伦 - 七里香.flac", "七里香", "周杰伦", "七里香", 2004, "Pop", 280_000L);
        bindLyric(qingtianId, "晴天", "周杰伦", "叶惠美", "LRC", "[ti:晴天]\n[00:01.00]故事的小黄花\n");
        bindArtwork(qingtianId, "qingtian.png");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics", qingtianId)
                .then()
                .statusCode(200)
                .body("trackId", equalTo(qingtianId.intValue()))
                .body("format", equalTo("LRC"))
                .body("content", equalTo("[ti:晴天]\n[00:01.00]故事的小黄花\n"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", qingtianId)
                .then()
                .statusCode(200)
                .body("available", equalTo(true))
                .body("hash", equalTo("晴天-hash"))
                .body("updatedAt", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork", qingtianId)
                .then()
                .statusCode(200)
                .contentType("image/png")
                .header("Cache-Control", notNullValue())
                .header("ETag", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", qingtianId)
                .then()
                .statusCode(200)
                .body("available", equalTo(true))
                .body("mimeType", equalTo("image/png"))
                .body("width", equalTo(3))
                .body("height", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/artists")
                .then()
                .statusCode(200)
                .body("[0].artistName", equalTo("周杰伦"))
                .body("[0].trackCount", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/artists/{artistName}/tracks", "周杰伦")
                .then()
                .statusCode(200)
                .body("total", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/albums")
                .then()
                .statusCode(200)
                .body("album", hasItem("叶惠美"))
                .body("album", hasItem("七里香"));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("album", "七里香")
                .when()
                .get("/api/open/v1/albums/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(qlxId.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("title", "晴天")
                .queryParam("artist", "周杰伦")
                .queryParam("album", "叶惠美")
                .queryParam("durationMs", 244_000)
                .when()
                .get("/api/open/v1/match/track")
                .then()
                .statusCode(200)
                .body("matched", equalTo(true))
                .body("score", equalTo(100))
                .body("track.id", equalTo(qingtianId.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("title", "不存在的歌")
                .when()
                .get("/api/open/v1/match/track")
                .then()
                .statusCode(200)
                .body("matched", equalTo(false))
                .body("track", nullValue());
    }

    @Test
    void lyricsBodyAndArtworkFileReturnSpecificNotFoundErrors() throws IOException {
        Long trackId = createTrack("无资源.flac", "无资源", "星语", "Demo", 2026, "Pop", 180_000L);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics", trackId)
                .then()
                .statusCode(404)
                .body("code", equalTo("LYRICS_NOT_FOUND"))
                .body("traceId", notNullValue());

        Path outsideRoot = Path.of("target/open-api-outside");
        Files.createDirectories(outsideRoot);
        Path outsideArtwork = outsideRoot.resolve("outside.png");
        writePng(outsideArtwork, 3, 2);
        bindArtworkPath(trackId, outsideArtwork);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork", trackId)
                .then()
                .statusCode(404)
                .body("code", equalTo("ARTWORK_NOT_FOUND"))
                .body("traceId", notNullValue());
    }

    @Test
    void missingTrackReturnsUnifiedOpenApiErrorAndAdminApiStillUsesExistingShape() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/999999")
                .then()
                .statusCode(404)
                .body("code", equalTo("TRACK_NOT_FOUND"))
                .body("message", equalTo("Track not found"))
                .body("traceId", notNullValue())
                .body("details", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    private Long createTrack(String fileName, String title, String artist, String album, Integer year, String genre, Long duration) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.album = album;
            track.albumArtist = artist;
            track.year = year;
            track.genre = genre;
            track.duration = duration;
            track.metadataStatus = "synced";
            track.lyricsStatus = "pending";
            track.artworkStatus = "missing";
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = workDir.resolve(fileName).toAbsolutePath().normalize().toString();
            trackFile.fileName = fileName;
            trackFile.fileExt = "flac";
            trackFile.fileSize = 5;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private void bindLyric(Long trackId, String title, String artist, String album, String format, String content) {
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = new Lyric();
            lyric.title = title;
            lyric.artist = artist;
            lyric.album = album;
            lyric.sourceType = "LOCAL_FILE";
            lyric.sourcePath = workDir.resolve(title + ".lrc").toAbsolutePath().normalize().toString();
            lyric.format = format;
            lyric.content = content;
            lyric.contentHash = title + "-hash";
            lyric.parseStatus = "PARSED";
            lyric.persist();

            SongLyric binding = new SongLyric();
            binding.songId = trackId;
            binding.lyricId = lyric.id;
            binding.matchType = "TITLE_ARTIST";
            binding.matchScore = 100;
            binding.isPrimary = true;
            binding.persist();
        });
    }

    private void bindArtwork(Long trackId, String fileName) throws IOException {
        Path imagePath = workDir.resolve(fileName);
        writePng(imagePath, 3, 2);
        bindArtworkPath(trackId, imagePath);
    }

    private void bindArtworkPath(Long trackId, Path imagePath) {
        QuarkusTransaction.requiringNew().run(() -> {
            Artwork artwork = new Artwork();
            artwork.filePath = imagePath.toAbsolutePath().normalize().toString();
            artwork.fileName = imagePath.getFileName().toString();
            artwork.fileExt = "png";
            artwork.mimeType = "image/png";
            artwork.fileSize = imageSize(imagePath);
            artwork.width = 3;
            artwork.height = 2;
            artwork.hash = "artwork-hash";
            artwork.sourceType = "local";
            artwork.sourcePath = artwork.filePath;
            artwork.title = "晴天";
            artwork.persist();

            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = trackId;
            binding.artworkId = artwork.id;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        });
    }

    private void writePng(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private long imageSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
