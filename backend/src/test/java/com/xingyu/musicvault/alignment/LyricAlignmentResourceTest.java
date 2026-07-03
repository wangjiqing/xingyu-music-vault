package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LyricAlignmentResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path MUSIC_ROOT = Path.of("target/test-music");
    private static final Path JOBS_ROOT = Path.of("target/test-alignment-jobs");
    private static final Path OUTSIDE_ROOT = Path.of("target/test-outside-music");
    private static final String TRUSTED_LYRICS = "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]故事的小黄花\n";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    LyricAlignmentJobStatusSynchronizer statusSynchronizer;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        deleteRecursively(JOBS_ROOT);
        deleteRecursively(MUSIC_ROOT);
        deleteRecursively(OUTSIDE_ROOT);
        Files.createDirectories(JOBS_ROOT);
        Files.createDirectories(MUSIC_ROOT);
        Files.createDirectories(OUTSIDE_ROOT);
        LyricAlignmentJob.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @AfterEach
    void restoreJobsRoot() throws IOException {
        if (Files.exists(JOBS_ROOT)) {
            JOBS_ROOT.toFile().setWritable(true, false);
        }
    }

    @Test
    void createsJobDirectoryAndInputFilesWithReadyLast() throws Exception {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("nested/周杰伦 - 晴天.flac"), TRUSTED_LYRICS);

        String jobId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "songId": %d,
                          "createdBy": "tester",
                          "sections": [{"name": "verse", "startLine": 1, "endLine": 3}],
                          "workerOptions": {"language": "zh"}
                        }
                        """.formatted(songId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(200)
                .body("id", matchesPattern("[0-9a-f\\-]{36}"))
                .body("songId", equalTo(songId.intValue()))
                .body("status", equalTo("QUEUED"))
                .body("reviewStatus", equalTo("NOT_AVAILABLE"))
                .body("importStatus", equalTo("NOT_IMPORTED"))
                .body("audioRelativePath", equalTo("nested/周杰伦 - 晴天.flac"))
                .body("workerAudioPath", equalTo("/worker/music/nested/周杰伦 - 晴天.flac"))
                .body("trustedLyricsHash", equalTo(sha256(TRUSTED_LYRICS)))
                .body("trustedLyricsSnapshot", equalTo(TRUSTED_LYRICS))
                .body("jobDir", nullValue())
                .body("requestSnapshot.jobId", notNullValue())
                .body("requestSnapshot.schemaVersion", equalTo(1))
                .body("requestSnapshot.audioPath", equalTo("/worker/music/nested/周杰伦 - 晴天.flac"))
                .body("requestSnapshot.lyricsPath", matchesPattern("/jobs/[0-9a-f\\-]{36}/trusted-lyrics.txt"))
                .body("requestSnapshot.outputDir", matchesPattern("/jobs/[0-9a-f\\-]{36}/result"))
                .body("requestSnapshot.sectionManifestPath", matchesPattern("/jobs/[0-9a-f\\-]{36}/sections.json"))
                .body("requestSnapshot.language", equalTo("zh"))
                .body("requestSnapshot.device", equalTo("cpu"))
                .extract()
                .path("id");

        Path jobDir = JOBS_ROOT.resolve(jobId);
        assertTrue(Files.isDirectory(jobDir));
        assertEquals(TRUSTED_LYRICS, Files.readString(jobDir.resolve("trusted-lyrics.txt"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(jobDir.resolve("request.json")));
        assertTrue(Files.exists(jobDir.resolve("sections.json")));
        assertTrue(Files.exists(jobDir.resolve("READY")));

        JsonNode requestJson = objectMapper.readTree(jobDir.resolve("request.json").toFile());
        assertEquals(9, requestJson.size());
        assertEquals(1, requestJson.path("schemaVersion").asInt());
        assertEquals(jobId, requestJson.path("jobId").asText());
        assertEquals("/worker/music/nested/周杰伦 - 晴天.flac", requestJson.path("audioPath").asText());
        assertEquals("/jobs/" + jobId + "/trusted-lyrics.txt", requestJson.path("lyricsPath").asText());
        assertEquals("/jobs/" + jobId + "/result", requestJson.path("outputDir").asText());
        assertEquals("/jobs/" + jobId + "/sections.json", requestJson.path("sectionManifestPath").asText());
        assertEquals("zh", requestJson.path("language").asText());
        assertEquals("cpu", requestJson.path("device").asText());

        FileTime readyTime = Files.getLastModifiedTime(jobDir.resolve("READY"));
        assertFalse(Files.getLastModifiedTime(jobDir.resolve("request.json")).compareTo(readyTime) > 0);
        assertFalse(Files.getLastModifiedTime(jobDir.resolve("trusted-lyrics.txt")).compareTo(readyTime) > 0);
        assertFalse(Files.getLastModifiedTime(jobDir.resolve("sections.json")).compareTo(readyTime) > 0);
        try (var paths = Files.list(jobDir)) {
            assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith(".")));
        }
    }

    @Test
    void listAndDetailExposeCreatedJob() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("王菲 - 红豆.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyric-alignment/jobs?status=QUEUED")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(jobId));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyric-alignment/jobs/{id}", jobId)
                .then()
                .statusCode(200)
                .body("id", equalTo(jobId))
                .body("status", equalTo("QUEUED"))
                .body("jobDir", nullValue());
    }

    @Test
    void rejectsMissingSongTrustedLyricAudioAndUnwritableDirectory() throws IOException {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": 404}")
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));

        Long noLyricSongId = createSong(MUSIC_ROOT.resolve("no-lyric.flac"));
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d}".formatted(noLyricSongId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));

        Long missingAudioSongId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("missing.flac"), TRUSTED_LYRICS);
        Files.delete(MUSIC_ROOT.resolve("missing.flac"));
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d}".formatted(missingAudioSongId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(400)
                .body("message", equalTo("Audio file not found"));

        Long unwritableSongId = createSongWithPrimaryLyric(
                MUSIC_ROOT.resolve("unwritable.flac"),
                "[ti:晴天]\n[ar:周杰伦]\n[00:02.00]第二份可信歌词\n"
        );
        JOBS_ROOT.toFile().setWritable(false, false);
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d}".formatted(unwritableSongId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(400);
        JOBS_ROOT.toFile().setWritable(true, false);
    }

    @Test
    void savesRequestSnapshotAndTrustedLyricsHash() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("hash.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals(sha256(TRUSTED_LYRICS), job.trustedLyricsHash);
        assertEquals(TRUSTED_LYRICS, job.trustedLyricsSnapshot);
        JsonNode snapshot = objectMapper.readTree(job.requestSnapshotJson);
        assertEquals(jobId, snapshot.path("jobId").asText());
        assertEquals(1, snapshot.path("schemaVersion").asInt());
        assertEquals("/worker/music/hash.flac", snapshot.path("audioPath").asText());
        assertEquals("/jobs/" + jobId + "/trusted-lyrics.txt", snapshot.path("lyricsPath").asText());
        assertEquals("/jobs/" + jobId + "/result", snapshot.path("outputDir").asText());
    }

    @Test
    void rejectsAudioPathOutsideConfiguredMusicRoots() throws IOException {
        Long songId = createSongWithPrimaryLyric(OUTSIDE_ROOT.resolve("escape.flac"), TRUSTED_LYRICS);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d}".formatted(songId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(400)
                .body("message", equalTo("Audio path is not mapped to the worker music directory"));

        assertEquals(0, LyricAlignmentJob.count());
    }

    @Test
    void doesNotOverwriteOriginalLyricAsset() throws IOException {
        Path sourceLyricFile = MUSIC_ROOT.resolve("lyrics/周杰伦 - 晴天.lrc");
        Files.createDirectories(sourceLyricFile.getParent());
        Files.writeString(sourceLyricFile, TRUSTED_LYRICS, StandardCharsets.UTF_8);
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("asset-safe.flac"), TRUSTED_LYRICS, sourceLyricFile);

        String jobId = createAlignmentJob(songId);

        assertEquals(TRUSTED_LYRICS, Files.readString(sourceLyricFile, StandardCharsets.UTF_8));
        assertNotEquals(sourceLyricFile.toAbsolutePath().normalize(), JOBS_ROOT.resolve(jobId).resolve("trusted-lyrics.txt").toAbsolutePath().normalize());
        assertEquals(1, Lyric.count());
    }

    @Test
    void creationFailureDoesNotLeaveQueuedJob() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("blocked.flac"), TRUSTED_LYRICS);
        Files.delete(JOBS_ROOT);
        Files.writeString(JOBS_ROOT, "not a directory", StandardCharsets.UTF_8);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d}".formatted(songId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(400);

        assertEquals(0, LyricAlignmentJob.count("status", "QUEUED"));
    }

    @Test
    void synchronizesReadyToQueued() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("ready.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        QuarkusTransaction.requiringNew().run(() -> {
            LyricAlignmentJob job = LyricAlignmentJob.findById(jobId);
            job.status = "CREATING";
            job.queuedAt = null;
        });

        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("QUEUED", job.status);
        assertNotNull(job.queuedAt);
    }

    @Test
    void synchronizesRunningAndKeepsStartedAtStable() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("running.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        writeMarker(jobId, "RUNNING");

        statusSynchronizer.synchronize(jobId);
        LocalDateTime firstStartedAt = findJob(jobId).startedAt;

        statusSynchronizer.synchronize(jobId);
        LyricAlignmentJob job = findJob(jobId);
        assertEquals("RUNNING", job.status);
        assertEquals(firstStartedAt, job.startedAt);
    }

    @Test
    void synchronizesSucceededAndReadsResultHashes() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("succeeded.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        writeStatusJson(jobId, """
                {
                  "schemaVersion": 1,
                  "jobId": "%s",
                  "status": "SUCCEEDED",
                  "updatedAt": "2026-07-02T00:00:00Z",
                  "result": {
                    "summary": {
                      "line_count": 1,
                      "aligned_line_count": 1,
                      "token_count": 3,
                      "coverage": 1.0,
                      "estimated_token_count": 0,
                      "skipped_line_count": 0
                    },
                    "warnings": []
                  }
                }
                """.formatted(jobId));
        writeResultFiles(jobId);

        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("COMPLETED", job.status);
        assertEquals("PENDING", job.reviewStatus);
        assertEquals("SUCCEEDED", job.workerOutcome);
        assertNotNull(job.completedAt);
        assertEquals(sha256("{\"segments\":[]}\n"), job.alignmentJsonHash);
        assertEquals(sha256("[00:01.00]故事的小黄花\n"), job.lrcHash);
        assertEquals(sha256("{\"lines\":[]}\n"), job.swlrcHash);
        assertEquals(sha256("{\"quality\":{}}\n"), job.reportHash);
        assertTrue(job.resultAvailable);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyric-alignment/jobs/{id}/artifacts/lrc", jobId)
                .then()
                .statusCode(200)
                .body(equalTo("[00:01.00]故事的小黄花\n"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyric-alignment/jobs/{id}", jobId)
                .then()
                .statusCode(200)
                .body("workerStatus.status", equalTo("SUCCEEDED"))
                .body("resultAvailable", equalTo(true))
                .body("resultSummary.lrcAvailable", equalTo(true))
                .body("resultSummary.workerStatus", equalTo("SUCCEEDED"))
                .body("resultSummary.lineCount", equalTo(1))
                .body("resultSummary.alignedLineCount", equalTo(1));
    }

    @Test
    void synchronizesNeedsReviewAsCompletedWithPendingReview() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("needs-review.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        writeResultFiles(jobId);
        writeMarker(jobId, "NEEDS_REVIEW");

        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("COMPLETED", job.status);
        assertEquals("PENDING", job.reviewStatus);
        assertEquals("NEEDS_REVIEW", job.workerOutcome);
    }

    @Test
    void synchronizesFailedAndAbandoned() throws IOException {
        Long failedSongId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("failed.flac"), TRUSTED_LYRICS);
        String failedJobId = createAlignmentJob(failedSongId);
        writeMarker(failedJobId, "FAILED");

        Long abandonedSongId = createSongWithPrimaryLyric(
                MUSIC_ROOT.resolve("abandoned.flac"),
                "[ti:晴天]\n[ar:周杰伦]\n[00:03.00]第三份可信歌词\n"
        );
        String abandonedJobId = createAlignmentJob(abandonedSongId);
        writeMarker(abandonedJobId, "ABANDONED");

        statusSynchronizer.synchronizeActiveJobs();

        LyricAlignmentJob failedJob = findJob(failedJobId);
        assertEquals("FAILED", failedJob.status);
        assertEquals("FAILED", failedJob.workerOutcome);
        assertNotNull(failedJob.failedAt);

        LyricAlignmentJob abandonedJob = findJob(abandonedJobId);
        assertEquals("ABANDONED", abandonedJob.status);
        assertEquals("ABANDONED", abandonedJob.workerOutcome);
    }

    @Test
    void corruptStatusJsonDoesNotInterruptFullSynchronization() throws IOException {
        Long corruptSongId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("corrupt.flac"), TRUSTED_LYRICS);
        String corruptJobId = createAlignmentJob(corruptSongId);
        Files.writeString(JOBS_ROOT.resolve(corruptJobId).resolve("status.json"), "{", StandardCharsets.UTF_8);

        Long runningSongId = createSongWithPrimaryLyric(
                MUSIC_ROOT.resolve("corrupt-neighbor.flac"),
                "[ti:晴天]\n[ar:周杰伦]\n[00:04.00]第四份可信歌词\n"
        );
        String runningJobId = createAlignmentJob(runningSongId);
        writeMarker(runningJobId, "RUNNING");

        statusSynchronizer.synchronizeActiveJobs();

        LyricAlignmentJob corruptJob = findJob(corruptJobId);
        assertEquals("Worker status JSON is not readable yet", corruptJob.syncMessage);
        LyricAlignmentJob runningJob = findJob(runningJobId);
        assertEquals("RUNNING", runningJob.status);
    }

    @Test
    void unsupportedStatusJsonSchemaVersionIsDiagnosticOnly() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("future-status.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        Files.writeString(
                JOBS_ROOT.resolve(jobId).resolve("status.json"),
                """
                {
                  "schemaVersion": 2,
                  "status": "SUCCEEDED"
                }
                """,
                StandardCharsets.UTF_8
        );

        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("QUEUED", job.status);
        assertEquals("Unsupported worker status schemaVersion: 2", job.syncMessage);
    }

    @Test
    void missingResultDirectoryDoesNotCrashSynchronization() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("no-result.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        writeMarker(jobId, "SUCCEEDED");

        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("COMPLETED", job.status);
        assertFalse(job.resultAvailable);
        assertEquals("Alignment result directory is not available yet", job.syncMessage);
    }

    @Test
    void completedJobRepeatSyncDoesNotChangeStartedOrCompletedAt() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("stable.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        writeMarker(jobId, "RUNNING");
        statusSynchronizer.synchronize(jobId);
        writeResultFiles(jobId);
        writeMarker(jobId, "SUCCEEDED");
        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob first = findJob(jobId);
        LocalDateTime startedAt = first.startedAt;
        LocalDateTime completedAt = first.completedAt;

        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob second = findJob(jobId);
        assertEquals(startedAt, second.startedAt);
        assertEquals(completedAt, second.completedAt);
    }

    @Test
    void artifactEndpointsRejectUnknownPathsAndReportMissingFiles() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("artifacts.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyric-alignment/jobs/{id}/artifacts/report", jobId)
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyric-alignment/jobs/{id}/artifacts/../report", jobId)
                .then()
                .statusCode(404);
    }

    private String createAlignmentJob(Long songId) {
        return given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d, \"createdBy\": \"tester\"}".formatted(songId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(200)
                .extract()
                .path("id");
    }

    private Long createSongWithPrimaryLyric(Path audioPath, String content) throws IOException {
        return createSongWithPrimaryLyric(audioPath, content, null);
    }

    private Long createSongWithPrimaryLyric(Path audioPath, String content, Path lyricSourcePath) throws IOException {
        Long songId = createSong(audioPath);
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = new Lyric();
            lyric.title = "晴天";
            lyric.artist = "周杰伦";
            lyric.album = "叶惠美";
            lyric.sourceType = "LOCAL_FILE";
            lyric.sourcePath = lyricSourcePath == null ? null : lyricSourcePath.toAbsolutePath().normalize().toString();
            lyric.content = content;
            lyric.contentHash = sha256(content);
            lyric.format = "LRC";
            lyric.parseStatus = "PARSED";
            lyric.persist();

            SongLyric binding = new SongLyric();
            binding.songId = songId;
            binding.lyricId = lyric.id;
            binding.matchType = "TITLE_ARTIST";
            binding.matchScore = 100;
            binding.isPrimary = true;
            binding.persist();
        });
        return songId;
    }

    private LyricAlignmentJob findJob(String jobId) {
        return QuarkusTransaction.requiringNew().call(() -> LyricAlignmentJob.findById(jobId));
    }

    private Long createSong(Path audioPath) throws IOException {
        Files.createDirectories(audioPath.getParent());
        Files.writeString(audioPath, "audio", StandardCharsets.UTF_8);
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = "晴天";
            track.normalizedTitle = "晴天";
            track.artist = "周杰伦";
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = audioPath.toAbsolutePath().normalize().toString();
            trackFile.fileName = audioPath.getFileName().toString();
            trackFile.fileExt = "flac";
            trackFile.fileSize = 5;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private void writeMarker(String jobId, String marker) throws IOException {
        Files.writeString(JOBS_ROOT.resolve(jobId).resolve(marker), "", StandardCharsets.UTF_8);
    }

    private void writeStatusJson(String jobId, String content) throws IOException {
        Files.writeString(JOBS_ROOT.resolve(jobId).resolve("status.json"), content, StandardCharsets.UTF_8);
    }

    private void writeResultFiles(String jobId) throws IOException {
        Path resultDir = JOBS_ROOT.resolve(jobId).resolve("result");
        Files.createDirectories(resultDir);
        Files.writeString(resultDir.resolve("alignment.json"), "{\"segments\":[]}\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("lyrics.lrc"), "[00:01.00]故事的小黄花\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("lyrics.swlrc"), "{\"lines\":[]}\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("report.json"), "{\"quality\":{}}\n", StandardCharsets.UTF_8);
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            path.toFile().setWritable(true, false);
            try (var stream = Files.walk(path)) {
                for (Path child : stream.sorted(Comparator.reverseOrder()).toList()) {
                    child.toFile().setWritable(true, false);
                    Files.deleteIfExists(child);
                }
            }
            return;
        }
        path.toFile().setWritable(true, false);
        Files.deleteIfExists(path);
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
