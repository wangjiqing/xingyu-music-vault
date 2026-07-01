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
                .body("requestSnapshot.audioRelativePath", equalTo("nested/周杰伦 - 晴天.flac"))
                .body("requestSnapshot.workerAudioPath", equalTo("/worker/music/nested/周杰伦 - 晴天.flac"))
                .body("requestSnapshot.trustedLyricsFile", equalTo("trusted-lyrics.txt"))
                .body("requestSnapshot.sectionsFile", equalTo("sections.json"))
                .body("requestSnapshot.workerOptions.language", equalTo("zh"))
                .extract()
                .path("id");

        Path jobDir = JOBS_ROOT.resolve(jobId);
        assertTrue(Files.isDirectory(jobDir));
        assertEquals(TRUSTED_LYRICS, Files.readString(jobDir.resolve("trusted-lyrics.txt"), StandardCharsets.UTF_8));
        assertTrue(Files.exists(jobDir.resolve("request.json")));
        assertTrue(Files.exists(jobDir.resolve("sections.json")));
        assertTrue(Files.exists(jobDir.resolve("READY")));

        JsonNode requestJson = objectMapper.readTree(jobDir.resolve("request.json").toFile());
        assertEquals(jobId, requestJson.path("jobId").asText());
        assertEquals(songId.longValue(), requestJson.path("songId").asLong());
        assertEquals(sha256(TRUSTED_LYRICS), requestJson.path("trustedLyricsHash").asText());

        FileTime readyTime = Files.getLastModifiedTime(jobDir.resolve("READY"));
        assertFalse(Files.getLastModifiedTime(jobDir.resolve("request.json")).compareTo(readyTime) > 0);
        assertFalse(Files.getLastModifiedTime(jobDir.resolve("trusted-lyrics.txt")).compareTo(readyTime) > 0);
        assertFalse(Files.getLastModifiedTime(jobDir.resolve("sections.json")).compareTo(readyTime) > 0);
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

        LyricAlignmentJob job = LyricAlignmentJob.findById(jobId);
        assertEquals(sha256(TRUSTED_LYRICS), job.trustedLyricsHash);
        assertEquals(TRUSTED_LYRICS, job.trustedLyricsSnapshot);
        JsonNode snapshot = objectMapper.readTree(job.requestSnapshotJson);
        assertEquals(jobId, snapshot.path("jobId").asText());
        assertEquals("hash.flac", snapshot.path("audioRelativePath").asText());
        assertEquals("/worker/music/hash.flac", snapshot.path("workerAudioPath").asText());
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
