package com.xingyu.musicvault.alignment;

import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(LyricAlignmentImportInvalidRootTest.InvalidRootProfile.class)
class LyricAlignmentImportInvalidRootTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path MUSIC_ROOT = Path.of("target/test-music");
    private static final Path JOBS_ROOT = Path.of("target/test-alignment-jobs");
    private static final Path OUTSIDE_ROOT = Path.of("target/test-outside-lyrics");
    private static final String TRUSTED_LYRICS = "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]故事的小黄花\n";

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
        LyricAlignmentJobEvent.deleteAll();
        LyricDraft.deleteAll();
        LyricAlignmentJob.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void importRejectsAlignmentLyricsRootOutsideLyricDirs() throws IOException {
        Long songId = createSongWithPrimaryLyric();
        String jobId = completeJob(songId);
        approve(jobId);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/admin/lyric-alignment/jobs/{id}/import", jobId)
                .then()
                .statusCode(400)
                .body("message", containsString("alignment-lyrics-root must match one configured lyric directory"));

        org.junit.jupiter.api.Assertions.assertEquals("IMPORT_FAILED", findJob(jobId).importStatus);
    }

    private Long createSongWithPrimaryLyric() throws IOException {
        Path audioPath = MUSIC_ROOT.resolve("invalid-root.flac");
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

            Lyric lyric = new Lyric();
            lyric.title = "晴天";
            lyric.artist = "周杰伦";
            lyric.sourceType = "LOCAL_FILE";
            lyric.content = TRUSTED_LYRICS;
            lyric.contentHash = sha256(TRUSTED_LYRICS);
            lyric.format = "LRC";
            lyric.parseStatus = "PARSED";
            lyric.persist();

            SongLyric binding = new SongLyric();
            binding.songId = trackFile.id;
            binding.lyricId = lyric.id;
            binding.matchType = "TITLE_ARTIST";
            binding.matchScore = 100;
            binding.isPrimary = true;
            binding.persist();
            return trackFile.id;
        });
    }

    private String completeJob(Long songId) throws IOException {
        String jobId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"songId\": %d, \"createdBy\": \"tester\"}".formatted(songId))
                .when()
                .post("/api/lyric-alignment/jobs")
                .then()
                .statusCode(200)
                .extract()
                .path("id");
        Path resultDir = JOBS_ROOT.resolve(jobId).resolve("result");
        Files.createDirectories(resultDir);
        Files.writeString(resultDir.resolve("alignment.json"), "{\"segments\":[]}\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("lyrics.lrc"), "[00:01.00]故事的小黄花\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("lyrics.swlrc"), "{\"lines\":[]}\n", StandardCharsets.UTF_8);
        Files.writeString(resultDir.resolve("report.json"), "{\"quality\":{}}\n", StandardCharsets.UTF_8);
        Files.writeString(JOBS_ROOT.resolve(jobId).resolve("SUCCEEDED"), "", StandardCharsets.UTF_8);
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
                .statusCode(200)
                .body("reviewStatus", equalTo("APPROVED"));
    }

    private LyricAlignmentJob findJob(String jobId) {
        return QuarkusTransaction.requiringNew().call(() -> LyricAlignmentJob.findById(jobId));
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (var stream = Files.walk(path)) {
                for (Path child : stream.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(child);
                }
            }
            return;
        }
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

    public static class InvalidRootProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("music-vault.alignment-lyrics-root", OUTSIDE_ROOT.toString());
        }
    }
}
