package com.xingyu.musicvault.metadata;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
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
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class MusicMetadataResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ALLOWED_MUSIC_ROOT = Path.of("target/test-music");

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ALLOWED_MUSIC_ROOT);
        musicDir = Files.createTempDirectory(ALLOWED_MUSIC_ROOT, "metadata-api-");
        MusicMetadataSyncAudit.deleteAll();
        MusicArtworkBinding.deleteAll();
        Artwork.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void compareReturnsDatabaseEmbeddedAndDiffs() throws Exception {
        Path file = musicDir.resolve("compare.mp3");
        createMp3(file,
                "-metadata", "title=File Title",
                "-metadata", "artist=File Artist",
                "-metadata", "album=File Album",
                "-metadata", "date=2024");
        Long musicId = createMusicRecordForPath(file, "DB Title", "DB Artist", null);
        setDatabaseMetadata(musicId, "DB Title", "DB Artist", null);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}/metadata/compare", musicId)
                .then()
                .statusCode(200)
                .body("musicId", equalTo(musicId.intValue()))
                .body("database.title", equalTo("DB Title"))
                .body("embedded.title", equalTo("File Title"))
                .body("database.keySet().size()", equalTo(3))
                .body("embedded.keySet().size()", equalTo(3))
                .body("diffs.find { it.field == 'title' }.databaseValue", equalTo("DB Title"))
                .body("diffs.find { it.field == 'artist' }.embeddedValue", equalTo("File Artist"))
                .body("diffs.find { it.field == 'album' }.embeddedValue", equalTo("File Album"))
                .body("diffs.find { it.field == 'year' }", nullValue())
                .body("diffs.find { it.field == 'duration' }", nullValue());
    }

    @Test
    void compareDoesNotReportAlbumArtistGenreOrTrackNumberDifferences() throws Exception {
        Path file = musicDir.resolve("compare-non-default-fields.mp3");
        createMp3(file,
                "-metadata", "title=Same Title",
                "-metadata", "artist=Same Artist",
                "-metadata", "album=Same Album",
                "-metadata", "album_artist=File Album Artist",
                "-metadata", "genre=File Genre",
                "-metadata", "track=7");
        Long musicId = createMusicRecordForPath(file, "Same Title", "Same Artist", "Same Album");
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            track.title = "Same Title";
            track.artist = "Same Artist";
            track.album = "Same Album";
            track.albumArtist = "Database Album Artist";
            track.year = 2023;
            track.genre = "Database Genre";
            track.trackNo = 3;
            track.duration = 0L;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}/metadata/compare", musicId)
                .then()
                .statusCode(200)
                .body("database.keySet().size()", equalTo(3))
                .body("embedded.keySet().size()", equalTo(3))
                .body("diffs.find { it.field == 'albumArtist' }", nullValue())
                .body("diffs.find { it.field == 'year' }", nullValue())
                .body("diffs.find { it.field == 'genre' }", nullValue())
                .body("diffs.find { it.field == 'trackNumber' }", nullValue())
                .body("diffs", hasSize(0));
    }

    @Test
    void applyFileToDatabaseWritesAudit() throws Exception {
        Long musicId = createTaggedMusic("file-to-db.mp3", "Embedded Title", "Embedded Artist", "Embedded Album");
        setDatabaseMetadata(musicId, "Old Title", "Old Artist", null);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-file-to-db", musicId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("direction", equalTo("file_to_db"))
                .body("afterDatabase.title", equalTo("Embedded Title"))
                .body("auditId", notNullValue());

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertEquals("Embedded Title", track.title);
            assertEquals("Embedded Album", track.album);
            assertEquals("embedded_tag", track.metadataSource);
            assertNotNull(track.metadataExtractedAt);
            assertEquals(1, MusicMetadataSyncAudit.count());
        });
    }

    @Test
    void applyFileToDatabaseDoesNotModifyAlbumArtistGenreOrTrackNumber() throws Exception {
        Path file = musicDir.resolve("file-to-db-keep-non-default-fields.mp3");
        createMp3(file,
                "-metadata", "title=Embedded Title",
                "-metadata", "artist=Embedded Artist",
                "-metadata", "album=Embedded Album",
                "-metadata", "album_artist=Embedded Album Artist",
                "-metadata", "date=2024",
                "-metadata", "genre=Embedded Genre",
                "-metadata", "track=7");
        Long musicId = createMusicRecordForPath(file, "Old Title", "Old Artist", "Old Album");
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            track.title = "Old Title";
            track.artist = "Old Artist";
            track.album = "Old Album";
            track.albumArtist = "Keep Album Artist";
            track.year = 2023;
            track.genre = "Keep Genre";
            track.trackNo = 3;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-file-to-db", musicId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("changedFields.find { it == 'albumArtist' }", nullValue())
                .body("changedFields.find { it == 'year' }", nullValue())
                .body("changedFields.find { it == 'genre' }", nullValue())
                .body("changedFields.find { it == 'trackNumber' }", nullValue())
                .body("afterDatabase.albumArtist", equalTo("Keep Album Artist"))
                .body("afterDatabase.year", equalTo(2023))
                .body("afterDatabase.genre", equalTo("Keep Genre"))
                .body("afterDatabase.trackNumber", equalTo(3));

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertEquals("Keep Album Artist", track.albumArtist);
            assertEquals(2023, track.year);
            assertEquals("Keep Genre", track.genre);
            assertEquals(3, track.trackNo);
        });
    }

    @Test
    void applyFileToDatabaseWithoutDiffDoesNotRefreshMetadataTimestamps() throws Exception {
        Long musicId = createTaggedMusic("file-to-db-no-diff.mp3", "Same Title", "Same Artist", "Same Album");
        LocalDateTime oldUpdatedAt = LocalDateTime.of(2026, 5, 20, 10, 0, 0, 123_000_000);
        LocalDateTime oldExtractedAt = LocalDateTime.of(2026, 5, 21, 10, 0, 0, 456_000_000);
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            track.title = "Same Title";
            track.artist = "Same Artist";
            track.album = "Same Album";
            track.albumArtist = null;
            track.year = null;
            track.genre = null;
            track.trackNo = null;
            track.duration = 0L;
            track.metadataUpdatedAt = oldUpdatedAt;
            track.metadataExtractedAt = oldExtractedAt;
            track.metadataSource = "database";
            track.metadataStatus = "matched";
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-file-to-db", musicId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("changedFields", hasSize(0))
                .body("auditId", notNullValue());

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertEquals(oldUpdatedAt, track.metadataUpdatedAt);
            assertEquals(oldExtractedAt, track.metadataExtractedAt);
            assertEquals("database", track.metadataSource);
            assertEquals("matched", track.metadataStatus);
            MusicMetadataSyncAudit audit = MusicMetadataSyncAudit.find("musicId", musicId).firstResult();
            assertNotNull(audit);
            assertEquals("SUCCESS", audit.status);
            assertEquals("[]", audit.changedFieldsJson);
        });
    }

    @Test
    void durationDifferenceIsNotReportedAndFileToDatabaseDoesNotSyncDuration() throws Exception {
        Long musicId = createTaggedMusic("duration-refresh.mp3", "Duration Title", "Duration Artist", "Duration Album");
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            track.title = "Duration Title";
            track.artist = "Duration Artist";
            track.album = "Duration Album";
            track.albumArtist = null;
            track.year = null;
            track.genre = null;
            track.trackNo = null;
            track.duration = 999L;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}/metadata/compare", musicId)
                .then()
                .statusCode(200)
                .body("diffs", hasSize(0))
                .body("database.keySet().size()", equalTo(3));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-file-to-db", musicId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("changedFields", hasSize(0));

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertEquals(999L, track.duration);
        });
    }

    @Test
    void applyDatabaseToFileWritesAudit() throws Exception {
        Long musicId = createTaggedMusic("db-to-file.mp3", "Old File Title", "Old File Artist", "Old File Album");
        setDatabaseMetadata(musicId, "DB Title", "DB Artist", "DB Album");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-db-to-file", musicId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("direction", equalTo("db_to_file"))
                .body("afterFile.title", equalTo("DB Title"))
                .body("auditId", notNullValue());

        QuarkusTransaction.requiringNew().run(() -> assertEquals(1, MusicMetadataSyncAudit.count()));
    }

    @Test
    void applyDatabaseToFileDoesNotWriteAlbumArtistGenreOrTrackTags() throws Exception {
        Path file = musicDir.resolve("db-to-file-non-default-tags.mp3");
        createMp3(file,
                "-metadata", "title=Old File Title",
                "-metadata", "artist=Old File Artist",
                "-metadata", "album=Old File Album",
                "-metadata", "album_artist=Original Album Artist",
                "-metadata", "date=2020",
                "-metadata", "genre=Original Genre",
                "-metadata", "track=9");
        Long musicId = createMusicRecordForPath(file, "DB Title", "DB Artist", "DB Album");
        setDatabaseMetadata(musicId, "DB Title", "DB Artist", "DB Album");
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            track.albumArtist = "Database Album Artist";
            track.year = 2023;
            track.genre = "Database Genre";
            track.trackNo = 3;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-db-to-file", musicId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SUCCESS"))
                .body("changedFields.find { it == 'albumArtist' }", nullValue())
                .body("changedFields.find { it == 'year' }", nullValue())
                .body("changedFields.find { it == 'genre' }", nullValue())
                .body("changedFields.find { it == 'trackNumber' }", nullValue())
                .body("afterFile.title", equalTo("DB Title"))
                .body("afterFile.albumArtist", equalTo("Original Album Artist"))
                .body("afterFile.year", equalTo(2020))
                .body("afterFile.genre", equalTo("Original Genre"))
                .body("afterFile.trackNumber", equalTo(9));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}/metadata/compare", musicId)
                .then()
                .statusCode(200)
                .body("database.keySet().size()", equalTo(3))
                .body("embedded.keySet().size()", equalTo(3))
                .body("diffs.find { it.field == 'albumArtist' }", nullValue())
                .body("diffs.find { it.field == 'year' }", nullValue())
                .body("diffs.find { it.field == 'genre' }", nullValue())
                .body("diffs.find { it.field == 'trackNumber' }", nullValue());
    }

    @Test
    void batchCompareRejectsMoreThanTwenty() {
        String ids = LongStream.rangeClosed(1, 21)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(","));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "musicIds": [%s]
                        }
                        """.formatted(ids))
                .when()
                .post("/api/music/metadata/compare")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void batchApplyAllowsPartialFailureAndSharesBatchId() throws Exception {
        Long successId = createTaggedMusic("batch-ok.mp3", "Batch File", "Batch Artist", "Batch Album");
        Long failedId = createMusicWithMissingFile("batch-missing.mp3");

        String batchId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "musicIds": [%d, %d]
                        }
                        """.formatted(successId, failedId))
                .when()
                .post("/api/music/metadata/apply-file-to-db")
                .then()
                .statusCode(200)
                .body("total", equalTo(2))
                .body("success", equalTo(1))
                .body("failed", equalTo(1))
                .body("items", hasSize(2))
                .body("items[0].status", equalTo("SUCCESS"))
                .body("items[1].status", equalTo("FAILED"))
                .body("batchId", notNullValue())
                .extract()
                .path("batchId");

        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(2, MusicMetadataSyncAudit.count("batchId", batchId));
            List<MusicMetadataSyncAudit> audits = MusicMetadataSyncAudit.list("batchId", batchId);
            assertEquals(1, audits.stream().filter(audit -> "SUCCESS".equals(audit.status)).count());
            assertEquals(1, audits.stream().filter(audit -> "FAILED".equals(audit.status)).count());
        });
    }

    @Test
    void scanNewSongExtractsEmbeddedMetadataAndExistingSongIsNotOverwritten() throws Exception {
        Path file = musicDir.resolve("scan.mp3");
        createMp3(file, "Scan Title", "Scan Artist", "Scan Album");
        Long scanJobId = createScanJob(musicDir);

        QuarkusTransaction.requiringNew().run(() -> ScanJob.<ScanJob>findById(scanJobId).status = "pending");
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/scan-jobs/{id}/run", scanJobId)
                .then()
                .statusCode(200);

        Long musicId = QuarkusTransaction.requiringNew().call(() -> {
            TrackFile trackFile = TrackFile.find("fileName", "scan.mp3").firstResult();
            Track track = Track.findById(trackFile.trackId);
            assertEquals("Scan Title", track.title);
            assertEquals("Scan Album", track.album);
            assertEquals("embedded_tag", track.metadataSource);
            track.title = "Manual Title";
            return trackFile.id;
        });

        createMp3(file, "Changed Tag Title", "Changed Artist", "Changed Album");
        Long secondJobId = createScanJob(musicDir);
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/scan-jobs/{id}/run", secondJobId)
                .then()
                .statusCode(200);

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertEquals("Manual Title", track.title);
        });
    }

    @Test
    void auditListSupportsPaginationAndFiltersAndDetailReturnsSnapshots() throws Exception {
        Long firstMusicId = createTaggedMusic("audit-list-a.mp3", "File A", "Artist A", "Album A");
        Long secondMusicId = createTaggedMusic("audit-list-b.mp3", "File B", "Artist B", "Album B");

        String batchId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "musicIds": [%d, %d]
                        }
                        """.formatted(firstMusicId, secondMusicId))
                .when()
                .post("/api/music/metadata/apply-file-to-db")
                .then()
                .statusCode(200)
                .body("success", equalTo(2))
                .extract()
                .path("batchId");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/metadata/audits?page=0&pageSize=1")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("total", equalTo(2))
                .body("page", equalTo(0))
                .body("pageSize", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("musicId", firstMusicId)
                .queryParam("batchId", batchId)
                .queryParam("direction", "file_to_db")
                .queryParam("status", "SUCCESS")
                .queryParam("rollbackStatus", "NOT_ROLLED_BACK")
                .queryParam("keyword", "audit-list-a")
                .when()
                .get("/api/music/metadata/audits")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].musicId", equalTo(firstMusicId.intValue()))
                .body("items[0].batchId", equalTo(batchId))
                .body("items[0].operationType", equalTo("APPLY"))
                .body("items[0].changedFields", hasSize(greaterThan(0)));

        Long auditId = QuarkusTransaction.requiringNew().call(() -> {
            MusicMetadataSyncAudit audit = MusicMetadataSyncAudit.find("musicId", firstMusicId).firstResult();
            return audit.id;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/metadata/audits/{auditId}", auditId)
                .then()
                .statusCode(200)
                .body("id", equalTo(auditId.intValue()))
                .body("beforeDatabase.title", equalTo("DB File A"))
                .body("afterDatabase.title", equalTo("File A"))
                .body("beforeFile.title", equalTo("File A"))
                .body("afterFile.title", equalTo("File A"))
                .body("rollbackAuditId", nullValue());
    }

    @Test
    void rollbackPreviewAndExecuteFileToDatabaseAudit() throws Exception {
        Long musicId = createTaggedMusic("rollback-db.mp3", "File Rollback", "File Artist", "File Album");
        setDatabaseMetadata(musicId, "Original DB", "Original Artist", "Original Album");

        Integer auditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-file-to-db", musicId)
                .then()
                .statusCode(200)
                .extract()
                .path("auditId");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/metadata/audits/{auditId}/rollback-preview", auditId)
                .then()
                .statusCode(200)
                .body("canRollback", equalTo(true))
                .body("rollbackTarget", equalTo("database"))
                .body("current.title", equalTo("File Rollback"))
                .body("target.title", equalTo("Original DB"))
                .body("diffs.find { it.field == 'title' }.embeddedValue", equalTo("Original DB"));

        Integer rollbackAuditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"confirm\": true}")
                .when()
                .post("/api/music/metadata/audits/{auditId}/rollback", auditId)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("rollbackAuditId", notNullValue())
                .extract()
                .path("rollbackAuditId");

        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            assertEquals("Original DB", track.title);
            MusicMetadataSyncAudit original = MusicMetadataSyncAudit.findById(auditId.longValue());
            MusicMetadataSyncAudit rollback = MusicMetadataSyncAudit.findById(rollbackAuditId.longValue());
            assertEquals("ROLLED_BACK", original.rollbackStatus);
            assertEquals("ROLLBACK", rollback.operationType);
            assertEquals(auditId.longValue(), rollback.rollbackOfAuditId);
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/metadata/audits/{auditId}/rollback-preview", auditId)
                .then()
                .statusCode(200)
                .body("canRollback", equalTo(false))
                .body("errorMessage", equalTo("Audit record has already been rolled back"));
    }

    @Test
    void rollbackPreviewAndExecuteDatabaseToFileAudit() throws Exception {
        Long musicId = createTaggedMusic("rollback-file.mp3", "Original File", "File Artist", "File Album");
        setDatabaseMetadata(musicId, "Database File Target", "Database Artist", "Database Album");

        Integer auditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-db-to-file", musicId)
                .then()
                .statusCode(200)
                .body("afterFile.title", equalTo("Database File Target"))
                .extract()
                .path("auditId");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/metadata/audits/{auditId}/rollback-preview", auditId)
                .then()
                .statusCode(200)
                .body("canRollback", equalTo(true))
                .body("rollbackTarget", equalTo("embedded_tag"))
                .body("current.title", equalTo("Database File Target"))
                .body("target.title", equalTo("Original File"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"confirm\": true}")
                .when()
                .post("/api/music/metadata/audits/{auditId}/rollback", auditId)
                .then()
                .statusCode(200)
                .body("success", equalTo(true))
                .body("rollbackAuditId", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}/metadata/compare", musicId)
                .then()
                .statusCode(200)
                .body("database.title", equalTo("Database File Target"))
                .body("embedded.title", equalTo("Original File"));
    }

    @Test
    void failedAndRollbackAuditRecordsCannotBeRolledBack() throws Exception {
        Long failedMusicId = createMusicWithMissingFile("rollback-failed-missing.mp3");

        Integer failedAuditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "musicIds": [%d]
                        }
                        """.formatted(failedMusicId))
                .when()
                .post("/api/music/metadata/apply-file-to-db")
                .then()
                .statusCode(200)
                .body("items[0].status", equalTo("FAILED"))
                .extract()
                .path("items[0].auditId");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/metadata/audits/{auditId}/rollback-preview", failedAuditId)
                .then()
                .statusCode(200)
                .body("canRollback", equalTo(false))
                .body("errorMessage", equalTo("Only SUCCESS audit records can be rolled back"));

        Long musicId = createTaggedMusic("rollback-record.mp3", "Rollback Record File", "Artist", "Album");
        Integer applyAuditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-file-to-db", musicId)
                .then()
                .statusCode(200)
                .extract()
                .path("auditId");
        Integer rollbackAuditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"confirm\": true}")
                .when()
                .post("/api/music/metadata/audits/{auditId}/rollback", applyAuditId)
                .then()
                .statusCode(200)
                .extract()
                .path("rollbackAuditId");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/metadata/audits/{auditId}/rollback-preview", rollbackAuditId)
                .then()
                .statusCode(200)
                .body("canRollback", equalTo(false))
                .body("errorMessage", equalTo("ROLLBACK audit records cannot be rolled back"));
    }

    @Test
    void batchRollbackRejectsMoreThanTwentyAndAllowsPartialFailure() throws Exception {
        String ids = LongStream.rangeClosed(1, 21)
                .mapToObj(Long::toString)
                .collect(java.util.stream.Collectors.joining(","));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "auditIds": [%s]
                        }
                        """.formatted(ids))
                .when()
                .post("/api/music/metadata/audits/rollback-preview")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));

        Long successMusicId = createTaggedMusic("batch-rollback-ok.mp3", "Batch Rollback", "Artist", "Album");
        Integer successAuditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{id}/metadata/apply-file-to-db", successMusicId)
                .then()
                .statusCode(200)
                .extract()
                .path("auditId");

        Long failedMusicId = createMusicWithMissingFile("batch-rollback-failed.mp3");
        Integer failedAuditId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "musicIds": [%d]
                        }
                        """.formatted(failedMusicId))
                .when()
                .post("/api/music/metadata/apply-file-to-db")
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].auditId");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "auditIds": [%d, %d]
                        }
                        """.formatted(successAuditId, failedAuditId))
                .when()
                .post("/api/music/metadata/audits/rollback-preview")
                .then()
                .statusCode(200)
                .body("total", equalTo(2))
                .body("canRollbackCount", equalTo(1))
                .body("cannotRollbackCount", equalTo(1));

        String batchId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "auditIds": [%d, %d],
                          "confirm": true
                        }
                        """.formatted(successAuditId, failedAuditId))
                .when()
                .post("/api/music/metadata/audits/rollback")
                .then()
                .statusCode(200)
                .body("total", equalTo(2))
                .body("success", equalTo(1))
                .body("failed", equalTo(1))
                .body("items[0].success", equalTo(true))
                .body("items[1].success", equalTo(false))
                .body("batchId", notNullValue())
                .extract()
                .path("batchId");

        QuarkusTransaction.requiringNew().run(() -> assertEquals(1, MusicMetadataSyncAudit.count("batchId = ?1 and operationType = ?2", batchId, "ROLLBACK")));
    }

    private Long createTaggedMusic(String fileName, String title, String artist, String album) throws Exception {
        Path file = musicDir.resolve(fileName);
        createMp3(file, title, artist, album);
        return createMusicRecordForPath(file, "DB " + title, "DB " + artist, "DB " + album);
    }

    private Long createMusicWithMissingFile(String fileName) {
        Path file = musicDir.resolve(fileName);
        return createMusicRecordForPath(file, "Missing", "Missing Artist", null);
    }

    private Long createMusicRecordForPath(Path file, String title, String artist, String album) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title == null ? null : title.toLowerCase(java.util.Locale.ROOT);
            track.artist = artist;
            track.album = album;
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = file.toAbsolutePath().normalize().toString();
            trackFile.fileName = file.getFileName().toString();
            trackFile.fileExt = extensionOf(file);
            trackFile.fileSize = Files.exists(file) ? Files.size(file) : 0;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private void setDatabaseMetadata(Long musicId, String title, String artist, String album) {
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            track.title = title;
            track.artist = artist;
            track.album = album;
            track.albumArtist = artist;
            track.year = 2023;
            track.genre = "DB Genre";
            track.trackNo = 3;
            track.duration = 1L;
        });
    }

    private Long createScanJob(Path path) {
        return QuarkusTransaction.requiringNew().call(() -> {
            ScanJob scanJob = new ScanJob();
            scanJob.jobType = "library_scan";
            scanJob.status = "pending";
            scanJob.musicDirs = path.toString();
            scanJob.persist();
            return scanJob.id;
        });
    }

    private void createMp3(Path path, String title, String artist, String album) throws Exception {
        createMp3(path,
                "-metadata", "title=" + title,
                "-metadata", "artist=" + artist,
                "-metadata", "album=" + album);
    }

    private void createMp3(Path path, String... metadataArgs) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("anullsrc=r=44100:cl=mono");
        command.add("-t");
        command.add("0.1");
        command.add("-q:a");
        command.add("9");
        command.addAll(List.of(metadataArgs));
        command.add(path.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("ffmpeg failed: " + output);
        }
    }

    private String extensionOf(Path file) {
        String fileName = file.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex < 0 ? "" : fileName.substring(dotIndex + 1);
    }
}
