package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.alignment.LyricAlignmentJob;
import com.xingyu.musicvault.alignment.LyricDraft;
import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class LyricDashboardResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path MUSIC_ROOT = Path.of("target/test-music");

    Path testDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        deleteRecursively(MUSIC_ROOT);
        Files.createDirectories(MUSIC_ROOT);
        testDir = Files.createTempDirectory(MUSIC_ROOT, "lyrics-dashboard-");
        LyricDailyRecommendation.deleteAll();
        LyricDraft.deleteAll();
        LyricAlignmentJob.deleteAll();
        MusicArtworkBinding.deleteAll();
        Artwork.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void statusOverviewAndMusicFilterUseUnifiedLyricStatus() throws IOException {
        Long swlrc = createMusicWithLyric("swlrc.flac", "SWLRC", true, true);
        Long lrc = createMusicWithLyric("lrc.flac", "LRC", false, true);
        Long none = createMusic("none.flac", "None");
        Long running = createMusic("running.flac", "Running");
        Long draft = createMusic("draft.flac", "Draft");
        Long failed = createMusic("failed.flac", "Failed");
        Long missingFileLyric = createMusicWithMissingSourceLyric("missing-source.flac", "Missing Source");

        createRunningJob(running);
        createPendingDraft(draft);
        createFailedJob(failed);

        assertMusicStatus(swlrc, "SWLRC_READY");
        assertMusicStatus(lrc, "LRC_READY");
        assertMusicStatus(none, "NO_LYRICS");
        assertMusicStatus(running, "ALIGNMENT_RUNNING");
        assertMusicStatus(draft, "DRAFT_PENDING");
        assertMusicStatus(failed, "FAILED");
        assertMusicStatus(missingFileLyric, "NO_LYRICS");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/lyrics/overview")
                .then()
                .statusCode(200)
                .body("totalSongs", equalTo(7))
                .body("songsWithLyrics", equalTo(2))
                .body("songsWithSwlrc", equalTo(1))
                .body("songsWithLrcOnly", equalTo(1))
                .body("songsWithoutLyrics", equalTo(2))
                .body("alignmentRunning", equalTo(1))
                .body("draftPending", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20&lyricStatus=MISSING_SWLRC")
                .then()
                .statusCode(200)
                .body("total", equalTo(3));
    }

    @Test
    void dailyRecommendationsAreStableSkipAndReplaceArePersisted() throws IOException {
        Long lrc = createMusicWithLyric("lrc-candidate.flac", "LRC Candidate", false, true);
        Long noLyrics = createMusic("no-lyrics-candidate.flac", "No Lyrics Candidate");
        createMusicWithLyric("ready.flac", "Ready", true, true);
        Long running = createMusic("running-candidate.flac", "Running Candidate");
        Long draft = createMusic("draft-candidate.flac", "Draft Candidate");
        createRunningJob(running);
        createPendingDraft(draft);
        createMusic("extra-candidate-1.flac", "Extra Candidate 1");
        createMusic("extra-candidate-2.flac", "Extra Candidate 2");
        createMusic("extra-candidate-3.flac", "Extra Candidate 3");
        createMusic("extra-candidate-4.flac", "Extra Candidate 4");
        createMusic("extra-candidate-5.flac", "Extra Candidate 5");

        var first = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/lyrics/recommendations/daily")
                .then()
                .statusCode(200)
                .body("items.size()", lessThanOrEqualTo(5))
                .body("items.music.id", not(hasItem(running.intValue())))
                .body("items.music.id", not(hasItem(draft.intValue())))
                .body("items.music.id", hasItem(lrc.intValue()))
                .body("items.music.id", hasItem(noLyrics.intValue()))
                .extract()
                .jsonPath();

        var second = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/lyrics/recommendations/daily")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath();
        org.junit.jupiter.api.Assertions.assertEquals(first.getList("items.id"), second.getList("items.id"));

        Integer firstRecommendationId = first.getInt("items[0].id");
        Integer firstMusicId = first.getInt("items[0].music.id");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/admin/lyrics/recommendations/{id}/skip", firstRecommendationId)
                .then()
                .statusCode(200)
                .body("items.music.id", not(hasItem(firstMusicId)));

        Integer replaceId = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/admin/lyrics/recommendations/daily")
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].id");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/admin/lyrics/recommendations/{id}/replace", replaceId)
                .then()
                .statusCode(200)
                .body("id", notNullValue());

        org.junit.jupiter.api.Assertions.assertTrue(LyricDailyRecommendation.count("actionStatus", "REPLACED") >= 1);
    }

    @Test
    void randomCandidatesValidateLimitAndExcludeSwlrcRunningAndDraft() throws IOException {
        createMusicWithLyric("ready.flac", "Ready", true, true);
        Long lrc = createMusicWithLyric("lrc.flac", "LRC", false, true);
        Long none = createMusic("none.flac", "None");
        Long running = createMusic("running.flac", "Running");
        Long draft = createMusic("draft.flac", "Draft");
        createRunningJob(running);
        createPendingDraft(draft);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"count\": 20}")
                .when()
                .post("/api/admin/lyrics/recommendations/random")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("items.id", hasItem(lrc.intValue()))
                .body("items.id", hasItem(none.intValue()))
                .body("items.id", not(hasItem(running.intValue())))
                .body("items.id", not(hasItem(draft.intValue())));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"count\": 21}")
                .when()
                .post("/api/admin/lyrics/recommendations/random")
                .then()
                .statusCode(400)
                .body("message", equalTo("count must be between 1 and 20"));
    }

    private void assertMusicStatus(Long id, String status) {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}", id)
                .then()
                .statusCode(200)
                .body("lyricStatus", equalTo(status));
    }

    private Long createMusic(String fileName, String title) throws IOException {
        Path file = testDir.resolve(fileName);
        Files.writeString(file, "audio");
        long size = Files.size(file);
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.artist = "Tester";
            track.album = "Album";
            track.persist();
            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = file.toAbsolutePath().normalize().toString();
            trackFile.fileName = fileName;
            trackFile.fileExt = "flac";
            trackFile.fileSize = size;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private Long createMusicWithLyric(String fileName, String title, boolean swlrc, boolean lrc) throws IOException {
        Long musicId = createMusic(fileName, title);
        Path lrcPath = testDir.resolve(fileName + ".lrc");
        Files.writeString(lrcPath, "[00:01.00]" + title + "\n");
        Path swlrcPath = testDir.resolve(fileName + ".swlrc");
        if (swlrc) {
            Files.writeString(swlrcPath, "[00:01.00]<00:01.00>" + title + "\n");
        }
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = createLyric(title, lrc ? lrcPath : null);
            if (swlrc) {
                lyric.swlrcPath = swlrcPath.toAbsolutePath().normalize().toString();
                lyric.swlrcHash = "swlrc-" + musicId;
            }
            bind(musicId, lyric.id);
        });
        return musicId;
    }

    private Long createMusicWithMissingSourceLyric(String fileName, String title) throws IOException {
        Long musicId = createMusic(fileName, title);
        Path missing = testDir.resolve(fileName + ".missing.lrc");
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = createLyric(title, missing);
            bind(musicId, lyric.id);
        });
        return musicId;
    }

    private Lyric createLyric(String title, Path sourcePath) {
        Lyric lyric = new Lyric();
        lyric.title = title;
        lyric.artist = "Tester";
        lyric.sourceType = "LOCAL_FILE";
        lyric.sourcePath = sourcePath == null ? null : sourcePath.toAbsolutePath().normalize().toString();
        lyric.content = "[00:01.00]" + title + "\n";
        lyric.contentHash = "hash-" + title;
        lyric.format = "LRC";
        lyric.parseStatus = "PARSED";
        lyric.persist();
        return lyric;
    }

    private void bind(Long musicId, Long lyricId) {
        SongLyric binding = new SongLyric();
        binding.songId = musicId;
        binding.lyricId = lyricId;
        binding.matchType = "TITLE_ARTIST";
        binding.matchScore = 100;
        binding.isPrimary = true;
        binding.persist();
    }

    private void createRunningJob(Long musicId) {
        LyricAlignmentJob job = baseJob(musicId);
        job.status = "RUNNING";
        QuarkusTransaction.requiringNew().run(job::persist);
    }

    private void createFailedJob(Long musicId) {
        LyricAlignmentJob job = baseJob(musicId);
        job.status = "FAILED";
        job.workerOutcome = "FAILED";
        job.failedAt = LocalDateTime.now();
        QuarkusTransaction.requiringNew().run(job::persist);
    }

    private LyricAlignmentJob baseJob(Long musicId) {
        LyricAlignmentJob job = new LyricAlignmentJob();
        job.id = java.util.UUID.randomUUID().toString();
        job.taskType = "LYRIC_ALIGNMENT";
        job.songId = musicId;
        job.status = "QUEUED";
        job.reviewStatus = "PENDING";
        job.importStatus = "NOT_IMPORTED";
        job.audioRelativePath = "audio.flac";
        job.workerAudioPath = "/worker/music/audio.flac";
        job.requestSnapshotJson = "{}";
        job.jobDir = testDir.resolve("job-" + job.id).toString();
        job.createdBy = "test";
        return job;
    }

    private void createPendingDraft(Long musicId) {
        QuarkusTransaction.requiringNew().run(() -> {
            LyricAlignmentJob job = baseJob(musicId);
            job.taskType = "LYRIC_DRAFT_EXTRACTION";
            job.status = "COMPLETED";
            job.persist();
            LyricDraft draft = new LyricDraft();
            draft.jobId = job.id;
            draft.musicId = musicId;
            draft.originalText = "draft";
            draft.originalTextHash = "draft-original-" + musicId;
            draft.editableText = "draft";
            draft.editableTextHash = "draft-editable-" + musicId;
            draft.draftStatus = "PENDING";
            draft.sourceType = "WORKER_EXTRACTION";
            draft.persist();
        });
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            for (Path current : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(current);
            }
        }
    }
}
