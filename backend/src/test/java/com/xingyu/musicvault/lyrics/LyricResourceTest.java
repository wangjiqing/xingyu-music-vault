package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
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
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class LyricResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ALLOWED_MUSIC_ROOT = Path.of("target/test-music");

    Path lyricDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ALLOWED_MUSIC_ROOT);
        lyricDir = Files.createTempDirectory(ALLOWED_MUSIC_ROOT, "lyrics-api-test-");
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void scanBindsLocalLrcAndExposesSongLyricStatus() throws IOException {
        Long songId = createSong("周杰伦 - 晴天.flac", "晴天", "周杰伦");
        Files.createDirectories(lyricDir.resolve("nested"));
        Files.writeString(
                lyricDir.resolve("nested/周杰伦 - 晴天.lrc"),
                """
                        [ti:晴天]
                        [ar:周杰伦]
                        [al:叶惠美]
                        [00:01.00]故事的小黄花
                        """
        );

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(lyricDir))
                .when()
                .post("/api/lyrics/scan")
                .then()
                .statusCode(200)
                .body("totalFiles", equalTo(1))
                .body("imported", equalTo(1))
                .body("matched", equalTo(1))
                .body("unmatched", equalTo(0))
                .body("failed", equalTo(0));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/songs/{songId}/lyrics", songId)
                .then()
                .statusCode(200)
                .body("songId", equalTo(songId.intValue()))
                .body("lyricStatus", equalTo("BOUND"))
                .body("lyricId", notNullValue())
                .body("title", equalTo("晴天"))
                .body("artist", equalTo("周杰伦"))
                .body("album", equalTo("叶惠美"))
                .body("format", equalTo("LRC"))
                .body("parseStatus", equalTo("PARSED"))
                .body("content", equalTo("""
                        [ti:晴天]
                        [ar:周杰伦]
                        [al:叶惠美]
                        [00:01.00]故事的小黄花
                        """));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items[0].id", equalTo(songId.intValue()))
                .body("items[0].lyricStatus", equalTo("BOUND"))
                .body("items[0].lyricId", notNullValue());
    }

    @Test
    void scanDoesNotOverwriteExistingPrimaryByDefault() throws IOException {
        Long songId = createSong("周杰伦 - 晴天.flac", "晴天", "周杰伦");
        Files.writeString(lyricDir.resolve("周杰伦 - 晴天.lrc"), "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]first\n");
        Files.writeString(lyricDir.resolve("周杰伦 - 晴天 live.lrc"), "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]second\n");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(lyricDir))
                .when()
                .post("/api/lyrics/scan")
                .then()
                .statusCode(200)
                .body("totalFiles", equalTo(2))
                .body("imported", equalTo(2))
                .body("matched", equalTo(1))
                .body("skippedBindings", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/songs/{songId}/lyrics", songId)
                .then()
                .statusCode(200)
                .body("lyricStatus", equalTo("BOUND"));
    }

    private Long createSong(String fileName, String title, String artist) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = lyricDir.resolve(fileName).toAbsolutePath().normalize().toString();
            trackFile.fileName = fileName;
            trackFile.fileExt = "flac";
            trackFile.fileSize = 5;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }
}
