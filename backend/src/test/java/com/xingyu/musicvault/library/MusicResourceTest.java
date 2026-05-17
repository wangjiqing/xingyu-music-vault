package com.xingyu.musicvault.library;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
class MusicResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ALLOWED_MUSIC_ROOT = Path.of("target/test-music");

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ALLOWED_MUSIC_ROOT);
        musicDir = Files.createTempDirectory(ALLOWED_MUSIC_ROOT, "music-api-test-");
        MusicArtworkBinding.deleteAll();
        Artwork.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void scanListAndGetMusic() throws IOException, InterruptedException {
        Files.writeString(musicDir.resolve("周杰伦 - 晴天.flac"), "first");
        Files.writeString(musicDir.resolve("note.txt"), "skip");

        Integer scanJobId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(musicDir))
                .when()
                .post("/api/music/scan")
                .then()
                .statusCode(202)
                .body("accepted", equalTo(true))
                .body("scanJobId", notNullValue())
                .body("message", equalTo("Scan accepted"))
                .extract()
                .path("scanJobId");

        waitForScanJobStatus(scanJobId.longValue(), "completed");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("晴天"))
                .body("items[0].artist", equalTo("周杰伦"))
                .body("items[0].fileExtension", equalTo("flac"))
                .body("items[0].fileSize", equalTo(5))
                .body("items[0].lastModifiedTime", notNullValue())
                .body("total", equalTo(1));

        Integer musicId = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music")
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].id");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}", musicId)
                .then()
                .statusCode(200)
                .body("id", equalTo(musicId))
                .body("title", equalTo("晴天"))
                .body("artist", equalTo("周杰伦"));
    }

    @Test
    void scanAcceptsMissingDirectoryAndMarksJobFailed() throws InterruptedException {
        Integer scanJobId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(musicDir.resolve("missing")))
                .when()
                .post("/api/music/scan")
                .then()
                .statusCode(202)
                .body("accepted", equalTo(true))
                .body("scanJobId", notNullValue())
                .extract()
                .path("scanJobId");

        ScanJob scanJob = waitForScanJobStatus(scanJobId.longValue(), "failed");
        assertNotNull(scanJob.errorMessage);
    }

    @Test
    void missingMusicReturnsNotFound() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void updateMetadataTrimsTextAndRefreshesTimestamp() {
        Long musicId = createMusic("old.flac", "Old Title", "Old Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "  青花瓷  ",
                          "artist": "  周杰伦 ",
                          "album": " 我很忙 ",
                          "year": 2007,
                          "trackNo": 3,
                          "genre": " Pop "
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", musicId)
                .then()
                .statusCode(200)
                .body("id", equalTo(musicId.intValue()))
                .body("title", equalTo("青花瓷"))
                .body("artist", equalTo("周杰伦"))
                .body("album", equalTo("我很忙"))
                .body("year", equalTo(2007))
                .body("trackNo", equalTo(3))
                .body("genre", equalTo("Pop"))
                .body("metadataUpdatedAt", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("青花瓷"))
                .body("items[0].artist", equalTo("周杰伦"))
                .body("items[0].album", equalTo("我很忙"))
                .body("items[0].year", equalTo(2007))
                .body("items[0].trackNo", equalTo(3))
                .body("items[0].genre", equalTo("Pop"))
                .body("items[0].metadataUpdatedAt", notNullValue());
    }

    @Test
    void updateMetadataConvertsBlankTextToNull() {
        Long musicId = createMusic("blank.flac", "Old Title", "Old Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "  ",
                          "artist": "",
                          "album": "   ",
                          "genre": "\\t"
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", musicId)
                .then()
                .statusCode(200)
                .body("title", equalTo("blank"))
                .body("artist", equalTo("Unknown"))
                .body("album", nullValue())
                .body("year", nullValue())
                .body("trackNo", nullValue())
                .body("genre", nullValue());
    }

    @Test
    void updateMetadataValidatesYearAndTrackNo() {
        Long musicId = createMusic("invalid.flac", "Invalid", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "year": 1899
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", musicId)
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "trackNo": 0
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", musicId)
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void updateMetadataMissingMusicReturnsNotFound() {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "青花瓷"
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", 999999)
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void updateMetadataPreservesNonMetadataFields() {
        Long musicId = createMusic("preserve.flac", "Old Title", "Old Artist");
        String expectedFilePath = musicDir.resolve("preserve.flac").toAbsolutePath().normalize().toString();
        BindingIds bindingIds = createPrimaryLyricAndArtwork(musicId);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "New Title",
                          "artist": "New Artist",
                          "album": "New Album",
                          "year": 2024,
                          "trackNo": 2,
                          "genre": "Pop"
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", musicId)
                .then()
                .statusCode(200)
                .body("title", equalTo("New Title"))
                .body("artist", equalTo("New Artist"))
                .body("filePath", equalTo(expectedFilePath))
                .body("lyricId", equalTo(bindingIds.lyricId().intValue()))
                .body("artworkId", equalTo(bindingIds.artworkId().intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}", musicId)
                .then()
                .statusCode(200)
                .body("filePath", equalTo(expectedFilePath))
                .body("fileName", equalTo("preserve.flac"))
                .body("lyricId", equalTo(bindingIds.lyricId().intValue()))
                .body("artworkId", equalTo(bindingIds.artworkId().intValue()));
    }

    @Test
    void listHandlesLegacyTrackFileWithoutTrackId() {
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = new TrackFile();
            trackFile.filePath = musicDir.resolve("legacy.flac").toAbsolutePath().normalize().toString();
            trackFile.fileName = "legacy.flac";
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("legacy"))
                .body("items[0].artist", equalTo("Unknown"))
                .body("items[0].lyricStatus", equalTo("NO_LYRIC"));
    }

    @Test
    void updateMetadataCreatesTrackForLegacyTrackFile() {
        Long musicId = QuarkusTransaction.requiringNew().call(() -> {
            TrackFile trackFile = new TrackFile();
            trackFile.filePath = musicDir.resolve("legacy-edit.flac").toAbsolutePath().normalize().toString();
            trackFile.fileName = "legacy-edit.flac";
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "新标题",
                          "artist": "新歌手"
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", musicId)
                .then()
                .statusCode(200)
                .body("title", equalTo("新标题"))
                .body("artist", equalTo("新歌手"))
                .body("metadataUpdatedAt", notNullValue());
    }

    private Long createMusic(String fileName, String title, String artist) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = musicDir.resolve(fileName).toAbsolutePath().normalize().toString();
            trackFile.fileName = fileName;
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private BindingIds createPrimaryLyricAndArtwork(Long musicId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Lyric lyric = new Lyric();
            lyric.title = "Preserve Lyric";
            lyric.artist = "Tester";
            lyric.sourceType = "local_file";
            lyric.sourcePath = musicDir.resolve("preserve.lrc").toAbsolutePath().normalize().toString();
            lyric.content = "[00:00.00]Preserve";
            lyric.contentHash = "preserve-lyric-" + musicId;
            lyric.format = "lrc";
            lyric.parseStatus = "success";
            lyric.persist();

            SongLyric songLyric = new SongLyric();
            songLyric.songId = musicId;
            songLyric.lyricId = lyric.id;
            songLyric.matchType = "manual";
            songLyric.matchScore = 100;
            songLyric.isPrimary = true;
            songLyric.persist();

            Artwork artwork = new Artwork();
            artwork.filePath = musicDir.resolve("preserve.png").toAbsolutePath().normalize().toString();
            artwork.fileName = "preserve.png";
            artwork.fileExt = "png";
            artwork.mimeType = "image/png";
            artwork.fileSize = 1;
            artwork.hash = "preserve-artwork-" + musicId;
            artwork.sourceType = "local";
            artwork.persist();

            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = musicId;
            binding.artworkId = artwork.id;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();

            return new BindingIds(lyric.id, artwork.id);
        });
    }

    private record BindingIds(Long lyricId, Long artworkId) {
    }

    private ScanJob waitForScanJobStatus(Long scanJobId, String expectedStatus) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            ScanJob scanJob = ScanJob.findById(scanJobId);
            if (scanJob != null && expectedStatus.equals(scanJob.status)) {
                assertEquals(expectedStatus, scanJob.status);
                return scanJob;
            }
            Thread.sleep(100);
        }
        fail("Scan job did not reach status: " + expectedStatus);
        return null;
    }
}
