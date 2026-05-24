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
        Long musicId = createTaggedMusic("compare.mp3", "File Title", "File Artist", "File Album");
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
                .body("diffs.find { it.field == 'title' }.databaseValue", equalTo("DB Title"))
                .body("diffs.find { it.field == 'album' }.embeddedValue", equalTo("File Album"))
                .body("diffs.find { it.field == 'duration' }", nullValue());
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
    void durationDifferenceIsNotReportedButFileToDatabaseRefreshesDuration() throws Exception {
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
                .body("database.duration", equalTo(999));

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
            org.junit.jupiter.api.Assertions.assertNotEquals(999L, track.duration);
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
