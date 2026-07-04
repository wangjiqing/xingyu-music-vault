package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import com.xingyu.musicvault.openapi.OpenApiCredential;
import com.xingyu.musicvault.openapi.OpenApiCredentialCryptoService;
import com.xingyu.musicvault.openapi.OpenApiRequestNonce;
import com.xingyu.musicvault.openapi.OpenApiScope;
import com.xingyu.musicvault.openapi.OpenApiTestClient;
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
import java.util.List;

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
// Tests use the test-only legacy bearer token. Production deployments must disable
// test-legacy-token; review and import are protected by the admin session cookie flow.
class LyricAlignmentResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path MUSIC_ROOT = Path.of("target/test-music");
    private static final Path JOBS_ROOT = Path.of("target/test-alignment-jobs");
    private static final Path ASSETS_ROOT = Path.of("target/test-alignment-assets");
    private static final Path OUTSIDE_ROOT = Path.of("target/test-outside-music");
    private static final String TRUSTED_LYRICS = "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]故事的小黄花\n";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    LyricAlignmentJobStatusSynchronizer statusSynchronizer;

    @Inject
    OpenApiCredentialCryptoService cryptoService;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        deleteRecursively(JOBS_ROOT);
        deleteRecursively(ASSETS_ROOT);
        deleteRecursively(MUSIC_ROOT);
        deleteRecursively(OUTSIDE_ROOT);
        Files.createDirectories(JOBS_ROOT);
        Files.createDirectories(ASSETS_ROOT);
        Files.createDirectories(MUSIC_ROOT);
        Files.createDirectories(OUTSIDE_ROOT);
        LyricAlignmentJobEvent.deleteAll();
        LyricDraft.deleteAll();
        LyricAlignmentJob.deleteAll();
        OpenApiRequestNonce.deleteAll();
        OpenApiCredential.deleteAll();
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
        if (Files.exists(ASSETS_ROOT)) {
            ASSETS_ROOT.toFile().setWritable(true, false);
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
    void createsMissingAlignmentJobsRootForLocalDevelopment() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("missing-root.flac"), TRUSTED_LYRICS);
        deleteRecursively(JOBS_ROOT);

        String jobId = createAlignmentJob(songId);

        assertTrue(Files.isDirectory(JOBS_ROOT));
        assertTrue(Files.isRegularFile(JOBS_ROOT.resolve(jobId).resolve("request.json")));
        assertTrue(Files.isRegularFile(JOBS_ROOT.resolve(jobId).resolve("READY")));
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
    void unsupportedFutureStatusJsonSchemaVersionIsDiagnosticOnly() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("future-status.flac"), TRUSTED_LYRICS);
        String jobId = createAlignmentJob(songId);
        Files.writeString(
                JOBS_ROOT.resolve(jobId).resolve("status.json"),
                """
                {
                  "schemaVersion": 3,
                  "status": "SUCCEEDED"
                }
                """,
                StandardCharsets.UTF_8
        );

        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("QUEUED", job.status);
        assertEquals("Unsupported worker status schemaVersion: 3", job.syncMessage);
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

    @Test
    void completedPendingJobCanBeApprovedAndRejectedOnce() throws IOException {
        Long approveSongId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("approve.flac"), TRUSTED_LYRICS);
        String approveJobId = completeJob(approveSongId, "SUCCEEDED");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"reviewNote\":\"可以导入\", \"reviewedBy\":\"reviewer\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/approve", approveJobId)
                .then()
                .statusCode(200)
                .body("reviewStatus", equalTo("APPROVED"))
                .body("reviewedBy", equalTo("reviewer"))
                .body("reviewNote", equalTo("可以导入"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"reviewNote\":\"重复\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/reject", approveJobId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        Long rejectSongId = createSongWithPrimaryLyric(
                MUSIC_ROOT.resolve("reject.flac"),
                "[ti:晴天]\n[ar:周杰伦]\n[00:05.00]第五份可信歌词\n"
        );
        String rejectJobId = completeJob(rejectSongId, "NEEDS_REVIEW");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"reviewNote\":\"时间轴不稳定\", \"reviewedBy\":\"reviewer\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/reject", rejectJobId)
                .then()
                .statusCode(200)
                .body("reviewStatus", equalTo("REJECTED"));

        assertEquals(2, LyricAlignmentJobEvent.count("action in ?1", java.util.List.of("APPROVED", "REJECTED")));
    }

    @Test
    void runningFailedAndAbandonedJobsCannotBeReviewed() throws IOException {
        Long runningSongId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("review-running.flac"), TRUSTED_LYRICS);
        String runningJobId = createAlignmentJob(runningSongId);
        writeMarker(runningJobId, "RUNNING");
        statusSynchronizer.synchronize(runningJobId);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"reviewNote\":\"no\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/approve", runningJobId)
                .then()
                .statusCode(409);

        Long failedSongId = createSongWithPrimaryLyric(
                MUSIC_ROOT.resolve("review-failed.flac"),
                "[ti:晴天]\n[ar:周杰伦]\n[00:06.00]第六份可信歌词\n"
        );
        String failedJobId = createAlignmentJob(failedSongId);
        writeMarker(failedJobId, "FAILED");
        statusSynchronizer.synchronize(failedJobId);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"reviewNote\":\"no\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/approve", failedJobId)
                .then()
                .statusCode(409);

        Long abandonedSongId = createSongWithPrimaryLyric(
                MUSIC_ROOT.resolve("review-abandoned.flac"),
                "[ti:晴天]\n[ar:周杰伦]\n[00:07.00]第七份可信歌词\n"
        );
        String abandonedJobId = createAlignmentJob(abandonedSongId);
        writeMarker(abandonedJobId, "ABANDONED");
        statusSynchronizer.synchronize(abandonedJobId);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"reviewNote\":\"no\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/approve", abandonedJobId)
                .then()
                .statusCode(409);
    }

    @Test
    void onlyApprovedJobCanBeImported() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("not-approved.flac"), TRUSTED_LYRICS);
        String jobId = completeJob(songId, "SUCCEEDED");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"importedBy\":\"importer\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        assertEquals("NOT_IMPORTED", findJob(jobId).importStatus);
    }

    @Test
    void importCopiesLrcAndSwlrcCreatesConfirmedLyricAndKeepsOriginalAsset() throws IOException {
        Path trustedSource = MUSIC_ROOT.resolve("lyrics/original.lrc");
        Files.createDirectories(trustedSource.getParent());
        Files.writeString(trustedSource, TRUSTED_LYRICS, StandardCharsets.UTF_8);
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("import.flac"), TRUSTED_LYRICS, trustedSource);
        Long originalLyricId = primaryLyricId(songId);
        String jobId = completeJob(songId, "SUCCEEDED");
        approve(jobId);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"importedBy\":\"importer\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(200)
                .body("jobId", equalTo(jobId))
                .body("importStatus", equalTo("IMPORTED"))
                .body("importedLyricId", notNullValue())
                .body("lrcHash", equalTo(sha256("[00:01.00]故事的小黄花\n")))
                .body("swlrcHash", equalTo(sha256("{\"lines\":[]}\n")));

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("IMPORTED", job.importStatus);
        assertNotNull(job.importedAt);
        assertEquals("importer", job.importedBy);
        assertNotNull(job.importedLyricId);

        Lyric imported = QuarkusTransaction.requiringNew().call(() -> Lyric.findById(job.importedLyricId));
        assertEquals("ALIGNMENT", imported.sourceType);
        assertEquals(jobId, imported.sourceTaskId);
        assertEquals(originalLyricId, imported.parentLyricsId);
        assertEquals(job.lrcHash, imported.contentHash);
        assertEquals(job.swlrcHash, imported.swlrcHash);
        Path expectedFinalDir = managedFinalDir(songId, jobId);
        assertEquals(expectedFinalDir.resolve("lyrics.lrc").toAbsolutePath().normalize().toString(), imported.sourcePath);
        assertEquals(expectedFinalDir.resolve("lyrics.swlrc").toAbsolutePath().normalize().toString(), imported.swlrcPath);
        assertEquals("[00:01.00]故事的小黄花\n", Files.readString(Path.of(imported.sourcePath), StandardCharsets.UTF_8));
        assertEquals("{\"lines\":[]}\n", Files.readString(Path.of(imported.swlrcPath), StandardCharsets.UTF_8));
        assertTrue(Files.isDirectory(expectedFinalDir));
        assertTrue(Files.isRegularFile(expectedFinalDir.resolve("lyrics.lrc")));
        assertTrue(Files.isRegularFile(expectedFinalDir.resolve("lyrics.swlrc")));
        assertEquals(TRUSTED_LYRICS, Files.readString(trustedSource, StandardCharsets.UTF_8));
        assertEquals(originalLyricId, QuarkusTransaction.requiringNew().call(() -> Lyric.<Lyric>findById(originalLyricId)).id);
        assertEquals(imported.id, primaryLyricId(songId));
        assertEquals("TITLE_ARTIST", primaryBinding(songId).matchType);

        OpenApiTestClient openApi = QuarkusTransaction.requiringNew()
                .call(() -> OpenApiTestClient.create(cryptoService, List.of(OpenApiScope.OPENAPI_READ)));

        openApi.get("/api/open/v1/tracks/" + songId + "/lyrics")
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics", songId)
                .then()
                .statusCode(200)
                .body("format", equalTo("LRC"))
                .body("content", equalTo("[00:01.00]故事的小黄花\n"));

        openApi.get("/api/open/v1/tracks/" + songId + "/lyrics/meta")
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", songId)
                .then()
                .statusCode(200)
                .body("wordLyricsAvailable", equalTo(true))
                .body("wordLyricsUrl", equalTo("/api/open/v1/tracks/" + songId + "/word-lyrics"))
                .body("lyricsVersionSource", equalTo("ALIGNMENT"));

        openApi.get("/api/open/v1/tracks/" + songId + "/word-lyrics")
                .when()
                .get("/api/open/v1/tracks/{id}/word-lyrics", songId)
                .then()
                .statusCode(200)
                .body("format", equalTo("SWLRC"))
                .body("content", equalTo("{\"lines\":[]}\n"));
    }

    @Test
    void repeatedImportIsIdempotent() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("idempotent.flac"), TRUSTED_LYRICS);
        String jobId = completeJob(songId, "SUCCEEDED");
        approve(jobId);

        Number firstLyricIdValue = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(200)
                .extract()
                .path("importedLyricId");
        Long firstLyricId = firstLyricIdValue.longValue();

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(200)
                .body("importedLyricId", equalTo(firstLyricId.intValue()));

        assertEquals(1, Lyric.count("sourceType = ?1 and sourceTaskId = ?2", "ALIGNMENT", jobId));
        assertEquals(1, SongLyric.count("songId = ?1 and lyricId = ?2", songId, firstLyricId));
        assertEquals(0, stagingDirectoryCount(songId, jobId));
    }

    @Test
    void importFailureDoesNotChangeExistingLyricBinding() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("missing-swlrc.flac"), TRUSTED_LYRICS);
        Long originalLyricId = primaryLyricId(songId);
        String jobId = completeJob(songId, "SUCCEEDED");
        approve(jobId);
        Files.delete(JOBS_ROOT.resolve(jobId).resolve("result").resolve("lyrics.swlrc"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(400);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("IMPORT_FAILED", job.importStatus);
        assertNotNull(job.importErrorMessage);
        assertEquals(originalLyricId, primaryLyricId(songId));
        assertEquals(1, Lyric.count());
    }

    @Test
    void unavailableManagedLyricDirectoryMarksImportFailedAndKeepsBinding() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("asset-dir-blocked.flac"), TRUSTED_LYRICS);
        Long originalLyricId = primaryLyricId(songId);
        String jobId = completeJob(songId, "SUCCEEDED");
        approve(jobId);
        Files.createDirectories(MUSIC_ROOT.resolve("alignment"));
        Files.writeString(MUSIC_ROOT.resolve("alignment").resolve(String.valueOf(songId)), "not a directory", StandardCharsets.UTF_8);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(400);

        assertEquals("IMPORT_FAILED", findJob(jobId).importStatus);
        assertEquals(originalLyricId, primaryLyricId(songId));
    }

    @Test
    void existingFinalDirWithDifferentContentRejectsImportAndCleansStaging() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("conflict.flac"), TRUSTED_LYRICS);
        Long originalLyricId = primaryLyricId(songId);
        String jobId = completeJob(songId, "SUCCEEDED");
        approve(jobId);
        Path finalDir = managedFinalDir(songId, jobId);
        Files.createDirectories(finalDir);
        Files.writeString(finalDir.resolve("lyrics.lrc"), "[00:01.00]tampered\n", StandardCharsets.UTF_8);
        Files.writeString(finalDir.resolve("lyrics.swlrc"), "{\"lines\":[]}\n", StandardCharsets.UTF_8);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        assertEquals("IMPORT_FAILED", findJob(jobId).importStatus);
        assertEquals(originalLyricId, primaryLyricId(songId));
        assertEquals(0, stagingDirectoryCount(songId, jobId));
        assertEquals("[00:01.00]tampered\n", Files.readString(finalDir.resolve("lyrics.lrc"), StandardCharsets.UTF_8));
    }

    @Test
    void databaseFailureKeepsPublishedFinalDirForRetryAndPreservesBinding() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("db-failure.flac"), TRUSTED_LYRICS);
        Long originalLyricId = primaryLyricId(songId);
        String jobId = completeJob(songId, "SUCCEEDED");
        approve(jobId);
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric conflicting = new Lyric();
            conflicting.title = "conflict";
            conflicting.sourceType = "DRAFT_CONFIRMED";
            conflicting.sourceTaskId = jobId;
            conflicting.content = "trusted text\n";
            conflicting.contentHash = sha256("trusted text\n");
            conflicting.format = "TEXT";
            conflicting.parseStatus = "PARSED";
            conflicting.persist();
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(500);

        Path finalDir = managedFinalDir(songId, jobId);
        assertTrue(Files.isRegularFile(finalDir.resolve("lyrics.lrc")));
        assertTrue(Files.isRegularFile(finalDir.resolve("lyrics.swlrc")));
        assertEquals(0, stagingDirectoryCount(songId, jobId));
        assertEquals("IMPORT_FAILED", findJob(jobId).importStatus);
        assertEquals(originalLyricId, primaryLyricId(songId));
        assertEquals(0, Lyric.count("sourceType = ?1 and sourceTaskId = ?2", "ALIGNMENT", jobId));
    }


    @Test
    void hashMismatchAndMissingResultDirectoryRejectImport() throws IOException {
        Long mismatchSongId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("hash-mismatch.flac"), TRUSTED_LYRICS);
        String mismatchJobId = completeJob(mismatchSongId, "SUCCEEDED");
        approve(mismatchJobId);
        Files.writeString(JOBS_ROOT.resolve(mismatchJobId).resolve("result").resolve("lyrics.lrc"), "tampered\n", StandardCharsets.UTF_8);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", mismatchJobId)
                .then()
                .statusCode(400)
                .body("message", equalTo("Alignment LRC result hash does not match the synchronized hash"));

        Long missingSongId = createSongWithPrimaryLyric(
                MUSIC_ROOT.resolve("missing-result.flac"),
                "[ti:晴天]\n[ar:周杰伦]\n[00:08.00]第八份可信歌词\n"
        );
        String missingJobId = completeJob(missingSongId, "SUCCEEDED");
        approve(missingJobId);
        deleteRecursively(JOBS_ROOT.resolve(missingJobId).resolve("result"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", missingJobId)
                .then()
                .statusCode(400)
                .body("message", equalTo("Alignment result directory is not available"));
    }

    @Test
    void nonAdminCannotReviewOrImport() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("auth.flac"), TRUSTED_LYRICS);
        String jobId = completeJob(songId, "SUCCEEDED");

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/approve", jobId)
                .then()
                .statusCode(401);

        approve(jobId);

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(401);
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

    private String completeJob(Long songId, String outcome) throws IOException {
        String jobId = createAlignmentJob(songId);
        writeResultFiles(jobId);
        writeMarker(jobId, outcome);
        statusSynchronizer.synchronize(jobId);
        return jobId;
    }

    private void approve(String jobId) {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"reviewNote\":\"ok\", \"reviewedBy\":\"reviewer\"}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/approve", jobId)
                .then()
                .statusCode(200);
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

    private Long primaryLyricId(Long songId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SongLyric binding = SongLyric.<SongLyric>find("songId = ?1 and isPrimary = true", songId).firstResult();
            return binding == null ? null : binding.lyricId;
        });
    }

    private SongLyric primaryBinding(Long songId) {
        return QuarkusTransaction.requiringNew()
                .call(() -> SongLyric.<SongLyric>find("songId = ?1 and isPrimary = true", songId).firstResult());
    }

    private Path managedFinalDir(Long songId, String jobId) {
        return MUSIC_ROOT.resolve("alignment").resolve(String.valueOf(songId)).resolve(jobId).toAbsolutePath().normalize();
    }

    private long stagingDirectoryCount(Long songId, String jobId) throws IOException {
        Path parent = MUSIC_ROOT.resolve("alignment").resolve(String.valueOf(songId));
        if (!Files.isDirectory(parent)) {
            return 0;
        }
        try (var stream = Files.list(parent)) {
            return stream
                    .filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().startsWith("." + jobId + ".staging-"))
                    .count();
        }
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
