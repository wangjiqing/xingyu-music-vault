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
import java.util.Comparator;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        deleteRecursively(ALLOWED_MUSIC_ROOT.resolve(".music-vault-trash"));
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

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertNull(track.title);
            assertNull(track.normalizedTitle);
        });
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
    void batchUpdateMetadataUpdatesSharedFields() {
        Long first = createMusic("batch-first.flac", "First", "Old Artist");
        Long second = createMusic("batch-second.flac", "Second", "Old Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d, %d],
                          "artist": "  周杰伦 ",
                          "album": " 叶惠美 ",
                          "year": 2003,
                          "genre": " Pop "
                        }
                        """.formatted(first, second))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(200)
                .body("updated", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("items.find { it.id == %d }.artist".formatted(first), equalTo("周杰伦"))
                .body("items.find { it.id == %d }.album".formatted(first), equalTo("叶惠美"))
                .body("items.find { it.id == %d }.year".formatted(first), equalTo(2003))
                .body("items.find { it.id == %d }.genre".formatted(first), equalTo("Pop"))
                .body("items.find { it.id == %d }.artist".formatted(second), equalTo("周杰伦"))
                .body("items.find { it.id == %d }.album".formatted(second), equalTo("叶惠美"))
                .body("items.find { it.id == %d }.year".formatted(second), equalTo(2003))
                .body("items.find { it.id == %d }.genre".formatted(second), equalTo("Pop"));
    }

    @Test
    void batchUpdateMetadataRefreshesMetadataUpdatedAt() {
        Long musicId = createMusic("batch-time.flac", "Time", "Old Artist");
        LocalDateTime oldUpdatedAt = LocalDateTime.now().minusDays(1);
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            track.metadataUpdatedAt = oldUpdatedAt;
            track.metadataStatus = "pending";
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d],
                          "artist": "New Artist"
                        }
                        """.formatted(musicId))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(200)
                .body("updated", equalTo(1));

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertNotNull(track.metadataUpdatedAt);
            assertTrue(track.metadataUpdatedAt.isAfter(oldUpdatedAt));
            assertEquals("pending", track.metadataStatus);
        });
    }

    @Test
    void batchUpdateMetadataRejectsEmptyIds() {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [],
                          "artist": "周杰伦"
                        }
                        """)
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void batchUpdateMetadataRejectsTooManyIds() {
        String ids = java.util.stream.LongStream.rangeClosed(1, 501)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(","));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%s],
                          "artist": "周杰伦"
                        }
                        """.formatted(ids))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void batchUpdateMetadataRejectsNoUpdatableFields() {
        Long musicId = createMusic("batch-no-fields.flac", "No Fields", "Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d],
                          "title": "Ignored Title",
                          "trackNo": 7
                        }
                        """.formatted(musicId))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void batchUpdateMetadataRejectsInvalidYear() {
        Long musicId = createMusic("batch-invalid-year.flac", "Invalid Year", "Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d],
                          "year": 1899
                        }
                        """.formatted(musicId))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void batchUpdateMetadataMissingIdReturnsNotFound() {
        Long musicId = createMusic("batch-missing-id.flac", "Missing Id", "Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d, 999999],
                          "artist": "New Artist"
                        }
                        """.formatted(musicId))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void batchUpdateMetadataRejectsTrashedAndDeletedMusic() throws IOException {
        Long active = createMusic("batch-active.flac", "Active", "Artist");
        Long trashed = createMusicWithFile("batch-trashed.flac", "Trashed", "Artist", "audio");
        Long deleted = createMusicWithFile("batch-deleted.flac", "Deleted", "Artist", "audio");
        trashMusic(trashed);
        trashMusic(deleted);
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}/trash", deleted)
                .then()
                .statusCode(200)
                .body("deleteStatus", equalTo("deleted"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d, %d],
                          "artist": "New Artist"
                        }
                        """.formatted(active, trashed))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d],
                          "artist": "New Artist"
                        }
                        """.formatted(deleted))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void batchUpdateMetadataIgnoresBlankStringFields() {
        Long musicId = createMusicWithFullMetadata(
                "batch-blank.flac",
                "Title",
                "Old Artist",
                "Old Album",
                1999,
                "Old Genre"
        );

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "ids": [%d],
                          "artist": "   ",
                          "album": "",
                          "year": 2001,
                          "genre": "\\t"
                        }
                        """.formatted(musicId))
                .when()
                .put("/api/music/metadata/batch")
                .then()
                .statusCode(200)
                .body("updated", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}", musicId)
                .then()
                .statusCode(200)
                .body("artist", equalTo("Old Artist"))
                .body("album", equalTo("Old Album"))
                .body("year", equalTo(2001))
                .body("genre", equalTo("Old Genre"));
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
                .body("items[0].lyricStatus", equalTo("NO_LYRICS"));
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

    @Test
    void getMusicFileReturnsFileDeleteFields() throws IOException {
        Long musicId = createMusicWithFile("file-info.flac", "File Info", "Tester", "audio");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}/file", musicId)
                .then()
                .statusCode(200)
                .body("id", equalTo(musicId.intValue()))
                .body("fileName", equalTo("file-info.flac"))
                .body("fileExtension", equalTo("flac"))
                .body("deleteStatus", equalTo("active"))
                .body("deletedAt", nullValue())
                .body("trashPath", nullValue());
    }

    @Test
    void deleteMissingMusicReturnsNotFound() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", 999999)
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void deleteMusicMovesFileToTrashAndHidesFromList() throws IOException {
        Long musicId = createMusicWithFile("delete-me.flac", "Delete Me", "Tester", "audio");
        Path originalPath = musicDir.resolve("delete-me.flac").toAbsolutePath().normalize();

        String trashPath = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(200)
                .body("id", equalTo(musicId.intValue()))
                .body("fileName", equalTo("delete-me.flac"))
                .body("deleteStatus", equalTo("trashed"))
                .body("deletedAt", notNullValue())
                .body("trashPath", notNullValue())
                .body("originalPath", equalTo(originalPath.toString()))
                .extract()
                .path("trashPath");

        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(originalPath));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(Path.of(trashPath)));
        org.junit.jupiter.api.Assertions.assertTrue(
                Path.of(trashPath).startsWith(ALLOWED_MUSIC_ROOT.toAbsolutePath().normalize().resolve(".music-vault-trash"))
        );

        TrackFile trashed = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("trashed", trashed.deleteStatus);
        assertNotNull(trashed.deletedAt);
        assertEquals(trashPath, trashed.trashPath);
        assertEquals(originalPath.toString(), trashed.originalPath);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(0))
                .body("total", equalTo(0));
    }

    @Test
    void trashListReturnsTrashedMusicRecords() throws IOException {
        Long musicId = createMusicWithFile("trash-list.flac", "Trash List", "Tester", "audio");
        Path originalPath = musicDir.resolve("trash-list.flac").toAbsolutePath().normalize();

        String trashPath = trashMusic(musicId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/trash")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].id", equalTo(musicId.intValue()))
                .body("[0].title", equalTo("Trash List"))
                .body("[0].artist", equalTo("Tester"))
                .body("[0].fileName", equalTo("trash-list.flac"))
                .body("[0].originalPath", equalTo(originalPath.toString()))
                .body("[0].trashPath", equalTo(trashPath))
                .body("[0].deletedAt", notNullValue())
                .body("[0].trashFileExists", equalTo(true))
                .body("[0].deleteStatus", equalTo("trashed"));
    }

    @Test
    void restoreMusicMovesFileBackAndMakesListVisible() throws IOException {
        Long musicId = createMusicWithFile("restore-me.flac", "Restore Me", "Tester", "audio");
        Path originalPath = musicDir.resolve("restore-me.flac").toAbsolutePath().normalize();
        String trashPath = trashMusic(musicId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/music/{id}/restore", musicId)
                .then()
                .statusCode(200)
                .body("id", equalTo(musicId.intValue()))
                .body("deleteStatus", equalTo("active"))
                .body("deletedAt", nullValue())
                .body("trashPath", nullValue())
                .body("originalPath", equalTo(originalPath.toString()));

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(originalPath));
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(Path.of(trashPath)));

        TrackFile restored = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("active", restored.deleteStatus);
        assertNull(restored.deletedAt);
        assertNull(restored.trashPath);
        assertEquals(originalPath.toString(), restored.originalPath);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(musicId.intValue()))
                .body("total", equalTo(1));
    }

    @Test
    void restoreMusicCreatesMissingOriginalDirectory() throws IOException {
        Path nestedPath = musicDir.resolve("nested").resolve("restore-nested.flac");
        Files.createDirectories(nestedPath.getParent());
        Files.writeString(nestedPath, "audio");
        Long musicId = createMusicRecordForPath(nestedPath, "Restore Nested", "Tester");

        trashMusic(musicId);
        Files.delete(nestedPath.getParent());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/music/{id}/restore", musicId)
                .then()
                .statusCode(200)
                .body("deleteStatus", equalTo("active"));

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(nestedPath));
    }

    @Test
    void restoreMusicReturnsConflictWhenOriginalPathExists() throws IOException {
        Long musicId = createMusicWithFile("restore-conflict.flac", "Restore Conflict", "Tester", "audio");
        Path originalPath = musicDir.resolve("restore-conflict.flac").toAbsolutePath().normalize();
        String trashPath = trashMusic(musicId);
        Files.writeString(originalPath, "new file");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/music/{id}/restore", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(originalPath));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(Path.of(trashPath)));
        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("trashed", trackFile.deleteStatus);
        assertNotNull(trackFile.deletedAt);
        assertEquals(trashPath, trackFile.trashPath);
    }

    @Test
    void restoreMusicReturnsConflictWhenTrashFileMissing() throws IOException {
        Long musicId = createMusicWithFile("restore-missing.flac", "Restore Missing", "Tester", "audio");
        String trashPath = trashMusic(musicId);
        Files.delete(Path.of(trashPath));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/music/{id}/restore", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("trashed", trackFile.deleteStatus);
        assertEquals(trashPath, trackFile.trashPath);
    }

    @Test
    void restoreActiveMusicReturnsConflict() {
        Long musicId = createMusic("restore-active.flac", "Restore Active", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/music/{id}/restore", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void permanentDeleteTrashFileRemovesFileAndMarksDeleted() throws IOException {
        Long musicId = createMusicWithFile("delete-trash.flac", "Delete Trash", "Tester", "audio");
        String trashPath = trashMusic(musicId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}/trash", musicId)
                .then()
                .statusCode(200)
                .body("id", equalTo(musicId.intValue()))
                .body("deleteStatus", equalTo("deleted"))
                .body("deletedAt", notNullValue())
                .body("trashPath", equalTo(trashPath));

        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(Path.of(trashPath)));
        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("deleted", trackFile.deleteStatus);
        assertNotNull(trackFile.deletedAt);
        assertEquals(trashPath, trackFile.trashPath);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/trash")
                .then()
                .statusCode(200)
                .body("", hasSize(0));
    }

    @Test
    void permanentDeleteActiveMusicReturnsConflict() {
        Long musicId = createMusic("delete-active-trash.flac", "Delete Active Trash", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}/trash", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void permanentDeleteRejectsTrashPathOutsideMusicVaultTrash() throws IOException {
        Path outsideTrashFile = musicDir.resolve("outside-trash.flac").toAbsolutePath().normalize();
        Files.writeString(outsideTrashFile, "audio");
        Long musicId = createTrashedMusicRecordForTrashPath(outsideTrashFile, "Outside Trash", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}/trash", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(outsideTrashFile));
        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("trashed", trackFile.deleteStatus);
    }

    @Test
    void deleteMusicUsesUniqueTrashFileNameOnConflict() throws IOException {
        Long musicId = createMusicWithFile("conflict.flac", "Conflict", "Tester", "audio");
        Path trashDir = ALLOWED_MUSIC_ROOT.resolve(".music-vault-trash").resolve(String.valueOf(musicId)).toAbsolutePath().normalize();
        Files.createDirectories(trashDir);
        Files.writeString(trashDir.resolve("conflict.flac"), "existing");

        String trashPath = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(200)
                .body("deleteStatus", equalTo("trashed"))
                .extract()
                .path("trashPath");

        org.junit.jupiter.api.Assertions.assertTrue(trashPath.endsWith("conflict-1.flac"));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(Path.of(trashPath)));
    }

    @Test
    void deleteMissingFileReturnsConflictAndKeepsDatabaseActive() {
        Long musicId = createMusic("missing-file.flac", "Missing File", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("active", trackFile.deleteStatus);
        assertNull(trackFile.deletedAt);
        assertNull(trackFile.trashPath);
    }

    @Test
    void deleteRejectsFileOutsideLibraryRoot() throws IOException {
        Path outsideBaseDir = Path.of(System.getProperty("java.io.tmpdir"));
        Path outsideDir = Files.createTempDirectory(outsideBaseDir, "music-delete-outside-");
        Path outsideFile = outsideDir.resolve("outside.flac");
        Files.writeString(outsideFile, "audio");
        Long musicId = createMusicRecordForPath(outsideFile, "Outside", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(outsideFile));
        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("active", trackFile.deleteStatus);
        assertNull(trackFile.deletedAt);
    }

    @Test
    void deleteRejectsFileAlreadyInsideMusicVaultTrash() throws IOException {
        Path trashDir = ALLOWED_MUSIC_ROOT.resolve(".music-vault-trash").resolve("manual");
        Files.createDirectories(trashDir);
        Path trashFile = trashDir.resolve("already-trashed.flac");
        Files.writeString(trashFile, "audio");
        Long musicId = createMusicRecordForPath(trashFile, "Already Trashed", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(trashFile));
        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("active", trackFile.deleteStatus);
        assertNull(trackFile.deletedAt);
    }

    @Test
    void deleteRejectsDirectoryPath() throws IOException {
        Path directory = Files.createDirectory(musicDir.resolve("directory.flac"));
        Long musicId = createMusicRecordForPath(directory, "Directory", "Tester");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(directory));
        TrackFile trackFile = QuarkusTransaction.requiringNew().call(() -> TrackFile.findById(musicId));
        assertEquals("active", trackFile.deleteStatus);
        assertNull(trackFile.deletedAt);
    }

    @Test
    void deleteAlreadyTrashedMusicReturnsConflict() throws IOException {
        Long musicId = createMusicWithFile("repeat.flac", "Repeat", "Tester", "audio");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void deleteDoesNotBreakExistingListFieldsBeforeDeletion() throws IOException {
        Long musicId = createMusicWithFile("field-check.flac", "Field Check", "Tester", "audio");
        BindingIds bindingIds = createPrimaryLyricAndArtwork(musicId);
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Field Check",
                          "artist": "Tester",
                          "album": "Album",
                          "year": 2024,
                          "trackNo": 1,
                          "genre": "Pop"
                        }
                        """)
                .when()
                .put("/api/music/{id}/metadata", musicId)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].year", equalTo(2024))
                .body("items[0].trackNo", equalTo(1))
                .body("items[0].genre", equalTo("Pop"))
                .body("items[0].lyricId", equalTo(bindingIds.lyricId().intValue()))
                .body("items[0].artworkId", equalTo(bindingIds.artworkId().intValue()))
                .body("items[0].deleteStatus", equalTo("active"))
                .body("items[0].deletedAt", nullValue());
    }

    @Test
    void statsReturnsCorrectCounts() throws IOException {
        // music1: complete metadata + artwork
        Long music1 = createMusicWithAlbum("stats-complete.flac", "Complete Title", "Complete Artist", "Complete Album");
        createArtworkOnly(music1);

        // music2: incomplete metadata (no album)
        Long music2 = createMusic("stats-incomplete.flac", "Incomplete Title", "Incomplete Artist");

        // music3: has lyrics
        Long music3 = createMusicWithAlbum("stats-lyrics.flac", "Lyrics Title", "Lyrics Artist", "Lyrics Album");
        createLyricOnly(music3);

        // music4: no track at all (legacy) — metadata incomplete
        Long music4 = QuarkusTransaction.requiringNew().call(() -> {
            TrackFile trackFile = new TrackFile();
            trackFile.filePath = musicDir.resolve("stats-legacy.flac").toAbsolutePath().normalize().toString();
            trackFile.fileName = "stats-legacy.flac";
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });

        // music5: trashed
        Long music5 = createMusicWithFile("stats-trashed.flac", "Trashed", "Artist", "audio");
        trashMusic(music5);

        // music6: permanently deleted (deleted status)
        Long music6 = createMusicWithFile("stats-deleted.flac", "Deleted", "Artist", "audio");
        String deletedTrashPath = trashMusic(music6);
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}/trash", music6)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/stats")
                .then()
                .statusCode(200)
                .body("total", equalTo(4))     // music1,2,3,4
                .body("metadataIncomplete", equalTo(2))  // music2 (no album) + music4 (no track)
                .body("lyricsReady", equalTo(1))  // music3
                .body("artworkReady", equalTo(1))  // music1
                .body("trashed", equalTo(1));  // music5 (music6 is deleted, not trashed)
    }

    @Test
    void statsReturnsAllZerosWhenNoData() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/stats")
                .then()
                .statusCode(200)
                .body("total", equalTo(0))
                .body("metadataIncomplete", equalTo(0))
                .body("lyricsReady", equalTo(0))
                .body("artworkReady", equalTo(0))
                .body("trashed", equalTo(0));
    }

    @Test
    void statsRestoredMusicDecreasesTrashedCount() throws IOException {
        Long musicId = createMusicWithFile("stats-restore.flac", "Restore", "Artist", "audio");
        trashMusic(musicId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/stats")
                .then()
                .statusCode(200)
                .body("trashed", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/music/{id}/restore", musicId)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/stats")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("trashed", equalTo(0));
    }

    @Test
    void artistsAggregatesActiveMusicAndSupportsFilteringPagingAndSorting() throws IOException {
        Long jayOne = createMusicWithAlbum("artist-jay-1.flac", "晴天", " 周杰伦 ", "叶惠美");
        Long jayTwo = createMusicWithAlbum("artist-jay-2.flac", "七里香", "周杰伦", "七里香");
        createLyricOnly(jayOne);
        createArtworkOnly(jayTwo);
        createMusic("artist-eason.flac", "十年", "陈奕迅");
        createMusicWithAlbum("artist-slash.flac", "分隔符测试", "AC/DC", "Back in Black");
        createMusicWithAlbum("artist-blank.flac", "空歌手", "   ", "未知专辑");
        Long trashed = createMusicWithFile("artist-trashed.flac", "已删除", "被删除歌手", "audio");
        trashMusic(trashed);

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = new TrackFile();
            trackFile.filePath = musicDir.resolve("artist-legacy.flac").toAbsolutePath().normalize().toString();
            trackFile.fileName = "artist-legacy.flac";
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/artists")
                .then()
                .statusCode(200)
                .body("page", equalTo(1))
                .body("pageSize", equalTo(20))
                .body("total", equalTo(4))
                .body("items", hasSize(4))
                .body("items.find { it.artist == '周杰伦' }.artistKey", equalTo("%E5%91%A8%E6%9D%B0%E4%BC%A6"))
                .body("items.find { it.artist == '周杰伦' }.trackCount", equalTo(2))
                .body("items.find { it.artist == '周杰伦' }.albumCount", equalTo(2))
                .body("items.find { it.artist == '周杰伦' }.lyricsCount", equalTo(1))
                .body("items.find { it.artist == '周杰伦' }.artworkCount", equalTo(1))
                .body("items.find { it.artist == '周杰伦' }.metadataIncompleteCount", equalTo(0))
                .body("items.find { it.artist == 'AC/DC' }.artistKey", equalTo("ac%2Fdc"))
                .body("items.find { it.artist == '未知歌手' }.artistKey", equalTo("__unknown__"))
                .body("items.find { it.artist == '未知歌手' }.trackCount", equalTo(2))
                .body("items.find { it.artist == '未知歌手' }.albumCount", equalTo(1))
                .body("items.find { it.artist == '未知歌手' }.lyricsCount", equalTo(0))
                .body("items.find { it.artist == '未知歌手' }.artworkCount", equalTo(0))
                .body("items.find { it.artist == '未知歌手' }.metadataIncompleteCount", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/artists?keyword=周")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("total", equalTo(1))
                .body("items[0].artist", equalTo("周杰伦"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/artists?page=2&pageSize=1&sort=nameAsc")
                .then()
                .statusCode(200)
                .body("page", equalTo(2))
                .body("pageSize", equalTo(1))
                .body("items", hasSize(1))
                .body("total", equalTo(4));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/artists?sort=albumCountDesc")
                .then()
                .statusCode(200)
                .body("items[0].artist", equalTo("周杰伦"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/artists?sort=metadataIncompleteDesc")
                .then()
                .statusCode(200)
                .body("items[0].artist", equalTo("未知歌手"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(6))
                .body("total", equalTo(6));
    }

    @Test
    void artistDetailReturnsOverviewAndAlbumGroupsByArtistKey() throws IOException {
        Long jayFirst = createMusicWithFullMetadata("detail-jay-1.flac", "晴天", " 周杰伦 ", "叶惠美", 2003, "Pop");
        Long jaySecond = createMusicWithFullMetadata("detail-jay-2.flac", "七里香", "周杰伦", "七里香", 2004, "Pop");
        Long jayNoAlbum = createMusic("detail-jay-no-album.flac", "无专辑", "周杰伦");
        createLyricOnly(jayFirst);
        createArtworkOnly(jaySecond);
        createMusicWithFullMetadata("detail-eason.flac", "十年", "陈奕迅", "黑白灰", 2003, "Pop");

        given()
                .header("Authorization", AUTHORIZATION)
                .urlEncodingEnabled(false)
                .when()
                .get("/api/music/artists/%E5%91%A8%E6%9D%B0%E4%BC%A6")
                .then()
                .statusCode(200)
                .body("artist", equalTo("周杰伦"))
                .body("artistKey", equalTo("%E5%91%A8%E6%9D%B0%E4%BC%A6"))
                .body("trackCount", equalTo(3))
                .body("albumCount", equalTo(2))
                .body("lyricsCount", equalTo(1))
                .body("artworkCount", equalTo(1))
                .body("metadataIncompleteCount", equalTo(1))
                .body("albums", hasSize(3))
                .body("albums.find { it.album == '叶惠美' }.albumKey", equalTo("%E5%8F%B6%E6%83%A0%E7%BE%8E"))
                .body("albums.find { it.album == '叶惠美' }.year", equalTo(2003))
                .body("albums.find { it.album == '叶惠美' }.trackCount", equalTo(1))
                .body("albums.find { it.album == '叶惠美' }.lyricsCount", equalTo(1))
                .body("albums.find { it.album == '七里香' }.coverMusicId", equalTo(jaySecond.intValue()))
                .body("albums.find { it.album == '七里香' }.sampleMusicId", equalTo(jaySecond.intValue()))
                .body("albums.find { it.album == '未知专辑' }.albumKey", equalTo("__unknown__"))
                .body("albums.find { it.album == '未知专辑' }.sampleMusicId", equalTo(jayNoAlbum.intValue()))
                .body("albums.find { it.album == '未知专辑' }.metadataIncompleteCount", equalTo(1));
    }

    @Test
    void artistDetailMatchesSlashArtistKeyAndUnknownArtist() {
        Long acdc = createMusicWithFullMetadata("detail-acdc.flac", "Back in Black", "AC/DC", "Back in Black", 1980, "Rock");
        Long unknownWithAlbum = createMusicWithAlbum("detail-unknown-album.flac", "空歌手", "   ", "孤儿专辑");
        Long legacy = QuarkusTransaction.requiringNew().call(() -> {
            TrackFile trackFile = new TrackFile();
            trackFile.filePath = musicDir.resolve("detail-legacy.flac").toAbsolutePath().normalize().toString();
            trackFile.fileName = "detail-legacy.flac";
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .urlEncodingEnabled(false)
                .when()
                .get("/api/music/artists/ac%2Fdc")
                .then()
                .statusCode(200)
                .body("artist", equalTo("AC/DC"))
                .body("artistKey", equalTo("ac%2Fdc"))
                .body("trackCount", equalTo(1))
                .body("albums", hasSize(1))
                .body("albums[0].sampleMusicId", equalTo(acdc.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/artists/__unknown__")
                .then()
                .statusCode(200)
                .body("artist", equalTo("未知歌手"))
                .body("artistKey", equalTo("__unknown__"))
                .body("trackCount", equalTo(2))
                .body("metadataIncompleteCount", equalTo(2))
                .body("albums", hasSize(2))
                .body("albums.find { it.album == '孤儿专辑' }.sampleMusicId", equalTo(unknownWithAlbum.intValue()))
                .body("albums.find { it.album == '未知专辑' }.sampleMusicId", equalTo(legacy.intValue()));
    }

    @Test
    void listFiltersByArtistKeyAndCombinesWithExistingFilters() {
        Long jayWithLyrics = createMusicWithFullMetadata("filter-jay-lyrics.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop");
        Long jayNoLyrics = createMusicWithFullMetadata("filter-jay-no-lyrics.flac", "七里香", "周杰伦", "七里香", 2004, "Pop");
        createLyricOnly(jayWithLyrics);
        createMusicWithFullMetadata("filter-eason.flac", "十年", "陈奕迅", "黑白灰", 2003, "Pop");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6&page=0&pageSize=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("total", equalTo(2))
                .body("items.find { it.id == %d }.artist".formatted(jayWithLyrics), equalTo("周杰伦"))
                .body("items.find { it.id == %d }.artist".formatted(jayNoLyrics), equalTo("周杰伦"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6&hasLyrics=true&keyword=晴")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(jayWithLyrics.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6&metadata=complete")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("total", equalTo(2));
    }

    @Test
    void albumsListSupportsSearchPagingSortingAndSpecialKeys() {
        Long chinese = createMusicWithFullMetadata("album-chinese.flac", "晴天", "周杰伦", " 叶惠美 ", 2001, "Pop");
        Long chineseLater = createMusicWithFullMetadata("album-chinese-2.flac", "同专辑", "周杰伦", "叶惠美", 2003, "Pop");
        Long slash = createMusicWithFullMetadata("album-slash.flac", "Back", "AC/DC", "Live/1979", 1979, "Rock");
        Long unknown = createMusic("album-unknown.flac", "无专辑", "周杰伦");
        createMusicWithFullMetadata("album-same-jay.flac", "Intro", "周杰伦", "Greatest Hits", 2008, "Pop");
        createMusicWithFullMetadata("album-same-eason.flac", "Intro", "陈奕迅", "Greatest Hits", 2010, "Pop");
        createMusicWithAlbumArtist("album-artist.flac", "Feat", "Solo Singer", "合作专辑", "Various Artists", 2012, "Pop");
        createLyricOnly(chinese);
        createArtworkOnly(chineseLater);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/albums")
                .then()
                .statusCode(200)
                .body("page", equalTo(1))
                .body("pageSize", equalTo(20))
                .body("total", equalTo(6))
                .body("items.find { it.album == '叶惠美' }.albumKey", equalTo("%E5%8F%B6%E6%83%A0%E7%BE%8E"))
                .body("items.find { it.album == '叶惠美' }.albumArtist", equalTo("周杰伦"))
                .body("items.find { it.album == '叶惠美' }.artistKey", equalTo("%E5%91%A8%E6%9D%B0%E4%BC%A6"))
                .body("items.find { it.album == '叶惠美' }.year", equalTo(2001))
                .body("items.find { it.album == '叶惠美' }.trackCount", equalTo(2))
                .body("items.find { it.album == '叶惠美' }.lyricsCount", equalTo(1))
                .body("items.find { it.album == '叶惠美' }.artworkCount", equalTo(1))
                .body("items.find { it.album == '叶惠美' }.coverMusicId", equalTo(chineseLater.intValue()))
                .body("items.find { it.album == 'Live/1979' }.albumKey", equalTo("live%2F1979"))
                .body("items.find { it.album == '未知专辑' }.albumKey", equalTo("__unknown__"))
                .body("items.find { it.album == '未知专辑' }.coverMusicId", equalTo(unknown.intValue()))
                .body("items.find { it.album == '合作专辑' }.albumArtist", equalTo("Various Artists"))
                .body("items.find { it.album == '合作专辑' }.artistKey", equalTo("various+artists"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/albums?keyword=叶惠")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].album", equalTo("叶惠美"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/albums?keyword=various")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].albumArtist", equalTo("Various Artists"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/albums?page=2&pageSize=2&sort=nameAsc")
                .then()
                .statusCode(200)
                .body("page", equalTo(2))
                .body("pageSize", equalTo(2))
                .body("items", hasSize(2))
                .body("total", equalTo(6));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/albums?sort=trackCountDesc")
                .then()
                .statusCode(200)
                .body("items[0].album", equalTo("叶惠美"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/albums?sort=yearDesc")
                .then()
                .statusCode(200)
                .body("items[0].album", equalTo("合作专辑"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/albums?artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6")
                .then()
                .statusCode(200)
                .body("items.findAll { it.album == 'Greatest Hits' }", hasSize(1))
                .body("items.find { it.album == 'Greatest Hits' }.albumArtist", equalTo("周杰伦"));

        given()
                .header("Authorization", AUTHORIZATION)
                .urlEncodingEnabled(false)
                .when()
                .get("/api/music/albums/detail?albumKey=%E5%8F%B6%E6%83%A0%E7%BE%8E&artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6")
                .then()
                .statusCode(200)
                .body("album", equalTo("叶惠美"))
                .body("trackCount", equalTo(2))
                .body("lyricsCount", equalTo(1))
                .body("artworkCount", equalTo(1))
                .body("metadataIncompleteCount", equalTo(0));

        given()
                .header("Authorization", AUTHORIZATION)
                .urlEncodingEnabled(false)
                .when()
                .get("/api/music/albums/detail?albumKey=live%2F1979&artistKey=ac%2Fdc")
                .then()
                .statusCode(200)
                .body("album", equalTo("Live/1979"))
                .body("coverMusicId", equalTo(slash.intValue()));
    }

    @Test
    void musicListFiltersByAlbumKeyAndArtistKeyWithoutRegressingArtistKey() {
        Long jayFirst = createMusicWithFullMetadata("album-filter-jay-1.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop");
        Long jaySecond = createMusicWithFullMetadata("album-filter-jay-2.flac", "爱在西元前", "周杰伦", "叶惠美", 2001, "Pop");
        Long jayOtherAlbum = createMusicWithFullMetadata("album-filter-jay-other.flac", "七里香", "周杰伦", "七里香", 2004, "Pop");
        createMusicWithFullMetadata("album-filter-eason.flac", "十年", "陈奕迅", "叶惠美", 2003, "Pop");
        Long slash = createMusicWithFullMetadata("album-filter-slash.flac", "Slash", "AC/DC", "Live/1979", 1979, "Rock");
        Long unknown = createMusic("album-filter-unknown.flac", "无专辑", "周杰伦");
        Long compilation = createMusicWithAlbumArtist("album-filter-compilation.flac", "Feat", "Solo Singer", "合作专辑", "Various Artists", 2012, "Pop");
        createLyricOnly(jayFirst);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?albumKey=%E5%8F%B6%E6%83%A0%E7%BE%8E&artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6&page=0&pageSize=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("total", equalTo(2))
                .body("items.find { it.id == %d }.title".formatted(jayFirst), equalTo("晴天"))
                .body("items.find { it.id == %d }.title".formatted(jaySecond), equalTo("爱在西元前"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?albumKey=%E5%8F%B6%E6%83%A0%E7%BE%8E&artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6&hasLyrics=true&keyword=晴")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(jayFirst.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .urlEncodingEnabled(false)
                .when()
                .get("/api/music?albumKey=live%2F1979&artistKey=ac%2Fdc")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(slash.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?albumKey=__unknown__&artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(unknown.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?albumKey=%E5%90%88%E4%BD%9C%E4%B8%93%E8%BE%91&artistKey=various+artists")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(compilation.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?artistKey=%E5%91%A8%E6%9D%B0%E4%BC%A6&page=0&pageSize=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(4))
                .body("total", equalTo(4))
                .body("items.find { it.id == %d }.title".formatted(jayOtherAlbum), equalTo("七里香"));
    }

    @Test
    void listFiltersByKeyword() throws IOException {
        createMusicWithFile("hello-world.flac", "Hello World", "Famous Artist", "audio");
        createMusicWithFile("famous-song.flac", "Another Title", "Famous Band", "audio");
        createMusicWithFile("other.flac", "Other Title", "Unknown", "audio");

        // "famous" matches "Famous Artist" and "Famous Band" and "famous-song.flac"
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?keyword=famous")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("total", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?keyword=unknown")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("total", equalTo(1))
                .body("items[0].title", equalTo("Other Title"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?keyword=nonexistent")
                .then()
                .statusCode(200)
                .body("items", hasSize(0))
                .body("total", equalTo(0));
    }

    @Test
    void listFiltersByYear() {
        createMusicWithYear("year2024.flac", "Song A", "Artist A", 2024);
        createMusicWithYear("year2025.flac", "Song B", "Artist B", 2025);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?year=2024")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("Song A"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?year=2099")
                .then()
                .statusCode(200)
                .body("items", hasSize(0))
                .body("total", equalTo(0));
    }

    @Test
    void listFiltersByGenre() {
        createMusicWithGenre("pop-song.flac", "Pop Song", "Artist", "Pop");
        createMusicWithGenre("rock-song.flac", "Rock Song", "Artist", "Rock");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?genre=Pop")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("Pop Song"));
    }

    @Test
    void listFiltersByMetadataCompleteness() {
        // complete: title + artist + album
        createMusicWithAlbum("complete.flac", "Title", "Artist", "Album");

        // incomplete: missing album
        createMusic("incomplete.flac", "Title", "Artist");

        // incomplete: no track at all
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = new TrackFile();
            trackFile.filePath = musicDir.resolve("legacy-inc.flac").toAbsolutePath().normalize().toString();
            trackFile.fileName = "legacy-inc.flac";
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?metadata=complete")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("total", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?metadata=incomplete")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("total", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?metadata=all")
                .then()
                .statusCode(200)
                .body("items", hasSize(3))
                .body("total", equalTo(3));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?metadata=invalid")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void listFiltersByHasLyrics() throws IOException {
        Long withLyrics = createMusic("has-lyrics.flac", "Has Lyrics", "Artist");
        createLyricOnly(withLyrics);

        createMusic("no-lyrics.flac", "No Lyrics", "Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?hasLyrics=true")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("Has Lyrics"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?hasLyrics=false")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("No Lyrics"));
    }

    @Test
    void listFiltersByHasArtwork() throws IOException {
        Long withArtwork = createMusic("has-artwork.flac", "Has Artwork", "Artist");
        createArtworkOnly(withArtwork);

        createMusic("no-artwork.flac", "No Artwork", "Artist");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?hasArtwork=true")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("Has Artwork"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?hasArtwork=false")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("No Artwork"));
    }

    @Test
    void listCombinesFilters() throws IOException {
        createMusicWithFile("match.flac", "Match Title", "Match Artist", "audio");
        createMusicWithFile("other.flac", "Other Title", "Match Artist", "audio");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?keyword=Match&size=50")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("total", equalTo(2));
    }

    @Test
    void listFilteringDoesNotIncludeTrashedOrDeleted() throws IOException {
        Long active = createMusicWithFile("filter-active.flac", "Active", "Artist", "audio");
        Long trashed = createMusicWithFile("filter-trashed.flac", "Trashed", "Artist", "audio");
        trashMusic(trashed);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?keyword=Artist")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(active.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("total", equalTo(1));
    }

    private void createLyricOnly(Long musicId) {
        Path lyricPath = musicDir.resolve("test-" + musicId + ".lrc").toAbsolutePath().normalize();
        try {
            Files.writeString(lyricPath, "[00:00.00]Test");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = new Lyric();
            lyric.title = "Test Lyric";
            lyric.artist = "Tester";
            lyric.sourceType = "local_file";
            lyric.sourcePath = lyricPath.toString();
            lyric.content = "[00:00.00]Test";
            lyric.contentHash = "lyric-hash-" + musicId;
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
        });
    }

    private void createArtworkOnly(Long musicId) {
        QuarkusTransaction.requiringNew().run(() -> {
            Artwork artwork = new Artwork();
            artwork.filePath = musicDir.resolve("test.png").toAbsolutePath().normalize().toString();
            artwork.fileName = "test.png";
            artwork.fileExt = "png";
            artwork.mimeType = "image/png";
            artwork.fileSize = 1;
            artwork.hash = "artwork-hash-" + musicId;
            artwork.sourceType = "local";
            artwork.persist();

            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = musicId;
            binding.artworkId = artwork.id;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        });
    }

    private Long createMusicWithAlbum(String fileName, String title, String artist, String album) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.album = album;
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

    private Long createMusicWithYear(String fileName, String title, String artist, int year) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.year = year;
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

    private Long createMusicWithGenre(String fileName, String title, String artist, String genre) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.genre = genre;
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

    private Long createMusicWithFullMetadata(String fileName, String title, String artist, String album, int year, String genre) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.album = album;
            track.year = year;
            track.genre = genre;
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

    private Long createMusicWithAlbumArtist(String fileName, String title, String artist, String album, String albumArtist, int year, String genre) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.album = album;
            track.albumArtist = albumArtist;
            track.year = year;
            track.genre = genre;
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

    private Long createMusicWithFile(String fileName, String title, String artist, String content) throws IOException {
        Files.writeString(musicDir.resolve(fileName), content);
        return createMusic(fileName, title, artist);
    }

    private Long createMusicRecordForPath(Path path, String title, String artist) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = path.toAbsolutePath().normalize().toString();
            trackFile.fileName = path.getFileName().toString();
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private Long createTrashedMusicRecordForTrashPath(Path trashPath, String title, String artist) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = musicDir.resolve(trashPath.getFileName()).toAbsolutePath().normalize().toString();
            trackFile.fileName = trashPath.getFileName().toString();
            trackFile.fileExt = "flac";
            trackFile.fileSize = 1;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.deletedAt = LocalDateTime.now();
            trackFile.trashPath = trashPath.toString();
            trackFile.originalPath = trackFile.filePath;
            trackFile.deleteStatus = "trashed";
            trackFile.persist();
            return trackFile.id;
        });
    }

    private String trashMusic(Long musicId) {
        return given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{id}", musicId)
                .then()
                .statusCode(200)
                .body("deleteStatus", equalTo("trashed"))
                .extract()
                .path("trashPath");
    }

    private BindingIds createPrimaryLyricAndArtwork(Long musicId) {
        Path lyricPath = musicDir.resolve("preserve-" + musicId + ".lrc").toAbsolutePath().normalize();
        try {
            Files.writeString(lyricPath, "[00:00.00]Preserve");
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            Lyric lyric = new Lyric();
            lyric.title = "Preserve Lyric";
            lyric.artist = "Tester";
            lyric.sourceType = "local_file";
            lyric.sourcePath = lyricPath.toString();
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

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path item : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(item);
            }
        }
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
