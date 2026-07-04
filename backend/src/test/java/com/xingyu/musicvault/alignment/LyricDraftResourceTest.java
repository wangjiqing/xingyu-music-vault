package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import com.xingyu.musicvault.openapi.OpenApiCredential;
import com.xingyu.musicvault.openapi.OpenApiRequestNonce;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class LyricDraftResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path MUSIC_ROOT = Path.of("target/test-music");
    private static final Path JOBS_ROOT = Path.of("target/test-alignment-jobs");
    private static final Path ASSETS_ROOT = Path.of("target/test-alignment-assets");
    private static final String TRUSTED_LYRICS = "[00:01.00]旧歌词\n";
    private static final String DRAFT_TEXT = "第一句歌词\n第二句歌词\n";

    @Inject
    ObjectMapper objectMapper;

    @Inject
    LyricAlignmentJobStatusSynchronizer statusSynchronizer;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        deleteRecursively(JOBS_ROOT);
        deleteRecursively(ASSETS_ROOT);
        deleteRecursively(MUSIC_ROOT);
        Files.createDirectories(JOBS_ROOT);
        Files.createDirectories(ASSETS_ROOT);
        Files.createDirectories(MUSIC_ROOT);
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
    void restoreAssetsRoot() {
        if (Files.exists(ASSETS_ROOT)) {
            ASSETS_ROOT.toFile().setWritable(true, false);
        }
    }

    @Test
    void createsV2DraftJobRequestWithoutTrustedLyricsAndReadyLast() throws Exception {
        Long songId = createSong(MUSIC_ROOT.resolve("draft.flac"));

        String jobId = createDraftJob(songId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/lyric-alignment/jobs/{id}", jobId)
                .then()
                .statusCode(200)
                .body("taskType", equalTo("LYRIC_DRAFT_EXTRACTION"))
                .body("lyricId", nullValue())
                .body("trustedLyricsHash", nullValue())
                .body("trustedLyricsSnapshot", nullValue())
                .body("requestSnapshot.schemaVersion", equalTo(2))
                .body("requestSnapshot.taskType", equalTo("LYRIC_DRAFT_EXTRACTION"))
                .body("requestSnapshot.audioPath", equalTo("/worker/music/draft.flac"))
                .body("requestSnapshot.outputDir", matchesPattern("/jobs/[0-9a-f\\-]{36}/result"))
                .body("requestSnapshot.asrModel", equalTo("small"))
                .body("requestSnapshot.skipSeparation", equalTo(false))
                .body("requestSnapshot.vadFilter", equalTo(true));

        Path jobDir = JOBS_ROOT.resolve(jobId);
        assertTrue(Files.exists(jobDir.resolve("request.json")));
        assertFalse(Files.exists(jobDir.resolve("trusted-lyrics.txt")));
        assertFalse(Files.exists(jobDir.resolve("sections.json")));
        assertTrue(Files.exists(jobDir.resolve("READY")));
        JsonNode requestJson = objectMapper.readTree(jobDir.resolve("request.json").toFile());
        assertEquals(2, requestJson.path("schemaVersion").asInt());
        assertEquals("LYRIC_DRAFT_EXTRACTION", requestJson.path("taskType").asText());
        assertEquals("/jobs/" + jobId + "/result", requestJson.path("outputDir").asText());
        FileTime readyTime = Files.getLastModifiedTime(jobDir.resolve("READY"));
        assertFalse(Files.getLastModifiedTime(jobDir.resolve("request.json")).compareTo(readyTime) > 0);
    }

    @Test
    void latestDraftContextReturnsDefaultsAndTrustedAssetSummary() throws IOException {
        Long songId = createSong(MUSIC_ROOT.resolve("latest-context.flac"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{musicId}/lyric-draft-jobs/latest", songId)
                .then()
                .statusCode(200)
                .body("musicId", equalTo(songId.intValue()))
                .body("defaultOptions.language", equalTo("zh"))
                .body("defaultOptions.asrModel", equalTo("medium"))
                .body("defaultOptions.skipSeparation", equalTo(false))
                .body("defaultOptions.vadFilter", equalTo(true))
                .body("latestJob", nullValue())
                .body("draft", nullValue())
                .body("trustedLyricsAsset", nullValue());

        String queuedJobId = createDraftJob(songId);
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{musicId}/lyric-draft-jobs/latest", songId)
                .then()
                .statusCode(200)
                .body("latestJob.id", equalTo(queuedJobId))
                .body("latestJob.workerSignals.ready", equalTo(true))
                .body("latestJob.workerSignals.running", equalTo(false))
                .body("latestJob.workerSignals.statusJsonAvailable", equalTo(false))
                .body("latestJob.workerSignals.resultDirectoryAvailable", equalTo(false))
                .body("latestJob.workerSignals.stageMessage", equalTo("READY 已写入，正在等待 Worker 领取任务"));

        writeDraftResultFiles(queuedJobId, DRAFT_TEXT);
        writeStatusJson(queuedJobId, """
                {
                  "schemaVersion": 2,
                  "taskType": "LYRIC_DRAFT_EXTRACTION",
                  "status": "SUCCEEDED"
                }
                """);
        statusSynchronizer.synchronize(queuedJobId);
        Number trustedLyricsIdValue = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"note\":\"已校对\", \"confirmedBy\":\"reviewer\"}")
                .when()
                .post("/api/admin/lyric-draft-jobs/{jobId}/confirm", queuedJobId)
                .then()
                .statusCode(200)
                .extract()
                .path("trustedLyricsId");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/music/{musicId}/lyric-draft-jobs/latest", songId)
                .then()
                .statusCode(200)
                .body("latestJob.id", equalTo(queuedJobId))
                .body("latestJob.taskType", equalTo("LYRIC_DRAFT_EXTRACTION"))
                .body("latestJob.draftStatus", equalTo("CONFIRMED"))
                .body("latestJob.confirmedTrustedLyricsId", equalTo(trustedLyricsIdValue.intValue()))
                .body("draft.draftStatus", equalTo("CONFIRMED"))
                .body("trustedLyricsAsset.id", equalTo(trustedLyricsIdValue.intValue()))
                .body("trustedLyricsAsset.sourceType", equalTo("DRAFT_CONFIRMED"));
    }

    @Test
    void rejectsDuplicateActiveDraftJobForSameSong() throws IOException {
        Long songId = createSong(MUSIC_ROOT.resolve("duplicate.flac"));
        createDraftJob(songId);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/music/{musicId}/lyric-draft-jobs", songId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void succeededDraftJobCreatesDraftOnceAndDoesNotReadAlignmentArtifacts() throws IOException {
        Long songId = createSong(MUSIC_ROOT.resolve("sync.flac"));
        String jobId = createDraftJob(songId);
        writeDraftResultFiles(jobId, DRAFT_TEXT);
        writeStatusJson(jobId, """
                {
                  "schemaVersion": 2,
                  "jobId": "%s",
                  "taskType": "LYRIC_DRAFT_EXTRACTION",
                  "status": "SUCCEEDED"
                }
                """.formatted(jobId));

        statusSynchronizer.synchronize(jobId);
        statusSynchronizer.synchronize(jobId);

        LyricAlignmentJob job = findJob(jobId);
        assertEquals("COMPLETED", job.status);
        assertEquals("NOT_AVAILABLE", job.reviewStatus);
        assertEquals("SUCCEEDED", job.workerOutcome);
        assertNull(job.lrcHash);
        assertNull(job.swlrcHash);
        assertNull(job.alignmentJsonHash);
        assertEquals(1, LyricDraft.count("jobId", jobId));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/lyric-draft-jobs/{jobId}/draft", jobId)
                .then()
                .statusCode(200)
                .body("jobId", equalTo(jobId))
                .body("musicId", equalTo(songId.intValue()))
                .body("executionStatus", equalTo("COMPLETED"))
                .body("draftStatus", equalTo("PENDING_REVIEW"))
                .body("originalText", equalTo(DRAFT_TEXT))
                .body("editableText", equalTo(DRAFT_TEXT))
                .body("originalTextHash", equalTo(sha256(DRAFT_TEXT)))
                .body("editableTextHash", equalTo(sha256(DRAFT_TEXT)));
    }

    @Test
    void missingOrEmptyCleanedTranscriptDoesNotCreateConfirmableDraft() throws IOException {
        Long missingSongId = createSong(MUSIC_ROOT.resolve("missing-cleaned.flac"));
        String missingJobId = createDraftJob(missingSongId);
        Files.createDirectories(JOBS_ROOT.resolve(missingJobId).resolve("result"));
        writeMarker(missingJobId, "SUCCEEDED");
        statusSynchronizer.synchronize(missingJobId);

        assertEquals("FAILED", findJob(missingJobId).status);
        assertEquals(0, LyricDraft.count("jobId", missingJobId));

        Long emptySongId = createSong(MUSIC_ROOT.resolve("empty-cleaned.flac"));
        String emptyJobId = createDraftJob(emptySongId);
        writeDraftResultFiles(emptyJobId, "\n");
        writeMarker(emptyJobId, "SUCCEEDED");
        statusSynchronizer.synchronize(emptyJobId);

        assertEquals("FAILED", findJob(emptyJobId).status);
        assertEquals(0, LyricDraft.count("jobId", emptyJobId));
    }

    @Test
    void editConfirmAndUseDraftConfirmedLyricAsAlignmentSource() throws IOException {
        Long songId = createSongWithPrimaryLyric(MUSIC_ROOT.resolve("confirm.flac"), TRUSTED_LYRICS);
        Long originalPrimaryLyricId = primaryLyricId(songId);
        String jobId = completeDraftJob(songId, DRAFT_TEXT);

        String edited = "人工校对第一句\n人工校对第二句\n";
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"text\":\"%s\", \"editedBy\":\"editor\"}".formatted(edited.replace("\n", "\\n")))
                .when()
                .put("/api/admin/lyric-draft-jobs/{jobId}/draft", jobId)
                .then()
                .statusCode(200)
                .body("draftStatus", equalTo("EDITING"))
                .body("editableText", equalTo(edited));

        Number trustedLyricsIdValue = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"note\":\"已校对\", \"confirmedBy\":\"reviewer\"}")
                .when()
                .post("/api/admin/lyric-draft-jobs/{jobId}/confirm", jobId)
                .then()
                .statusCode(200)
                .body("draftStatus", equalTo("CONFIRMED"))
                .body("trustedLyricsId", notNullValue())
                .extract()
                .path("trustedLyricsId");
        Long trustedLyricsId = trustedLyricsIdValue.longValue();

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-draft-jobs/{jobId}/confirm", jobId)
                .then()
                .statusCode(200)
                .body("trustedLyricsId", equalTo(trustedLyricsId.intValue()));

        Lyric confirmed = QuarkusTransaction.requiringNew().call(() -> Lyric.findById(trustedLyricsId));
        assertEquals("DRAFT_CONFIRMED", confirmed.sourceType);
        assertEquals(jobId, confirmed.sourceTaskId);
        assertEquals(edited, confirmed.content);
        assertEquals(sha256(edited), confirmed.contentHash);
        assertEquals(sha256(edited), confirmed.sourceTextHash);
        assertEquals(originalPrimaryLyricId, primaryLyricId(songId));
        assertEquals(1, Lyric.count("sourceType = ?1 and sourceTaskId = ?2", "DRAFT_CONFIRMED", jobId));

        String alignmentJobId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d, \"sourceLyricsAssetId\": %d}".formatted(songId, trustedLyricsId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(200)
                .body("lyricId", equalTo(trustedLyricsId.intValue()))
                .body("trustedLyricsHash", equalTo(sha256(edited)))
                .extract()
                .path("id");
        assertEquals(edited, Files.readString(JOBS_ROOT.resolve(alignmentJobId).resolve("trusted-lyrics.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void confirmedAndRejectedDraftsCannotBeEditedAndRejectRequiresNote() throws IOException {
        Long confirmedSongId = createSong(MUSIC_ROOT.resolve("confirmed-no-edit.flac"));
        String confirmedJobId = completeDraftJob(confirmedSongId, DRAFT_TEXT);
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-draft-jobs/{jobId}/confirm", confirmedJobId)
                .then()
                .statusCode(200);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"text\":\"new\"}")
                .when()
                .put("/api/admin/lyric-draft-jobs/{jobId}/draft", confirmedJobId)
                .then()
                .statusCode(409);

        Long rejectedSongId = createSong(MUSIC_ROOT.resolve("reject-draft.flac"));
        String rejectedJobId = completeDraftJob(rejectedSongId, DRAFT_TEXT);
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-draft-jobs/{jobId}/reject", rejectedJobId)
                .then()
                .statusCode(400);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"rejectNote\":\"识别错误\", \"rejectedBy\":\"reviewer\"}")
                .when()
                .post("/api/admin/lyric-draft-jobs/{jobId}/reject", rejectedJobId)
                .then()
                .statusCode(200)
                .body("draftStatus", equalTo("REJECTED"))
                .body("rejectNote", equalTo("识别错误"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"text\":\"new\"}")
                .when()
                .put("/api/admin/lyric-draft-jobs/{jobId}/draft", rejectedJobId)
                .then()
                .statusCode(409);
    }

    @Test
    void nonAdminCannotCreateOrMutateDraft() throws IOException {
        Long songId = createSong(MUSIC_ROOT.resolve("auth-draft.flac"));

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/music/{musicId}/lyric-draft-jobs", songId)
                .then()
                .statusCode(401);

        String jobId = completeDraftJob(songId, DRAFT_TEXT);
        given()
                .contentType(ContentType.JSON)
                .body("{\"text\":\"new\"}")
                .when()
                .put("/api/admin/lyric-draft-jobs/{jobId}/draft", jobId)
                .then()
                .statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-draft-jobs/{jobId}/confirm", jobId)
                .then()
                .statusCode(401);
    }

    private String createDraftJob(Long songId) {
        return given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "language": "zh",
                          "asrModel": "small",
                          "skipSeparation": false,
                          "vadFilter": true,
                          "conditionOnPreviousText": false,
                          "keepSuspectedMetadata": false,
                          "createdBy": "tester"
                        }
                        """)
                .when()
                .post("/api/admin/music/{musicId}/lyric-draft-jobs", songId)
                .then()
                .statusCode(200)
                .extract()
                .path("id");
    }

    private String completeDraftJob(Long songId, String text) throws IOException {
        String jobId = createDraftJob(songId);
        writeDraftResultFiles(jobId, text);
        writeStatusJson(jobId, """
                {
                  "schemaVersion": 2,
                  "taskType": "LYRIC_DRAFT_EXTRACTION",
                  "status": "SUCCEEDED"
                }
                """);
        statusSynchronizer.synchronize(jobId);
        return jobId;
    }

    private void writeDraftResultFiles(String jobId, String text) throws IOException {
        Path resultDir = JOBS_ROOT.resolve(jobId).resolve("result");
        Files.createDirectories(resultDir);
        Files.writeString(resultDir.resolve("transcript.cleaned.txt"), text, StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("transcript.raw.txt"), text + "raw\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("transcript.segments.json"), "{\"segments\":[]}\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("report.json"), "{\"segment_count\":0}\n", StandardCharsets.UTF_8);
    }

    private void writeStatusJson(String jobId, String content) throws IOException {
        Files.writeString(JOBS_ROOT.resolve(jobId).resolve("status.json"), content, StandardCharsets.UTF_8);
    }

    private void writeMarker(String jobId, String marker) throws IOException {
        Files.writeString(JOBS_ROOT.resolve(jobId).resolve(marker), "", StandardCharsets.UTF_8);
    }

    private LyricAlignmentJob findJob(String jobId) {
        return QuarkusTransaction.requiringNew().call(() -> LyricAlignmentJob.findById(jobId));
    }

    private Long createSongWithPrimaryLyric(Path audioPath, String content) throws IOException {
        Long songId = createSong(audioPath);
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = new Lyric();
            lyric.title = "晴天";
            lyric.artist = "周杰伦";
            lyric.sourceType = "LOCAL_FILE";
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

    private Long primaryLyricId(Long songId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            SongLyric binding = SongLyric.<SongLyric>find("songId = ?1 and isPrimary = true", songId).firstResult();
            return binding == null ? null : binding.lyricId;
        });
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
