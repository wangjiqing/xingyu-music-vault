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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

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

        Lyric lyric = Lyric.find("title", "晴天").firstResult();

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyrics?keyword={keyword}&bindStatus=BOUND&parseStatus=SUCCESS&sourceType=LOCAL_FILE", "晴天")
                .then()
                .statusCode(200)
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(lyric.id.intValue()))
                .body("items[0].title", equalTo("晴天"))
                .body("items[0].artist", equalTo("周杰伦"))
                .body("items[0].sourceType", equalTo("LOCAL_FILE"))
                .body("items[0].format", equalTo("LRC"))
                .body("items[0].parseStatus", equalTo("SUCCESS"))
                .body("items[0].bindStatus", equalTo("BOUND"))
                .body("items[0].boundSongId", equalTo(songId.intValue()))
                .body("items[0].boundSongTitle", equalTo("晴天"))
                .body("items[0].boundSongArtist", equalTo("周杰伦"))
                .body("items[0].matchType", equalTo("TITLE_ARTIST"))
                .body("items[0].matchScore", equalTo(100))
                .body("items[0].isPrimary", equalTo(true));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyrics/{id}", lyric.id)
                .then()
                .statusCode(200)
                .body("id", equalTo(lyric.id.intValue()))
                .body("title", equalTo("晴天"))
                .body("contentHash", equalTo(lyric.contentHash))
                .body("parseStatus", equalTo("SUCCESS"))
                .body("bindStatus", equalTo("BOUND"))
                .body("boundSong.songId", equalTo(songId.intValue()))
                .body("boundSong.title", equalTo("晴天"))
                .body("boundSongs[0].songId", equalTo(songId.intValue()))
                .body("content", equalTo("""
                        [ti:晴天]
                        [ar:周杰伦]
                        [al:叶惠美]
                        [00:01.00]故事的小黄花
                        """));
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

    @Test
    void scanReusesUnboundSourcePathRecordWhenDeletedLrcIsRestoredWithChangedContent() throws IOException {
        Long songId = createSong("周杰伦 - 晴天.flac", "晴天", "周杰伦");
        Path lrcPath = lyricDir.resolve("周杰伦 - 晴天.lrc");
        String firstContent = "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]first\n";
        String secondContent = "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]second\n";
        Files.writeString(lrcPath, firstContent);

        scanLyrics()
                .body("totalFiles", equalTo(1))
                .body("imported", equalTo(1))
                .body("matched", equalTo(1));

        Lyric firstLyric = Lyric.find("sourcePath", lrcPath.toRealPath().toString()).firstResult();
        Long originalLyricId = firstLyric.id;
        org.junit.jupiter.api.Assertions.assertEquals(sha256(firstContent), firstLyric.contentHash);

        Files.delete(lrcPath);
        scanLyrics()
                .body("totalFiles", equalTo(0))
                .body("failed", equalTo(0));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/songs/{songId}/lyrics", songId)
                .then()
                .statusCode(200)
                .body("lyricStatus", equalTo("NO_LYRIC"))
                .body("lyricId", nullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyrics?bindStatus=UNBOUND")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(originalLyricId.intValue()));

        Files.writeString(lrcPath, secondContent);
        scanLyrics()
                .body("totalFiles", equalTo(1))
                .body("imported", equalTo(0))
                .body("matched", equalTo(1))
                .body("failed", equalTo(0));

        Lyric restoredLyric = Lyric.findById(originalLyricId);
        org.junit.jupiter.api.Assertions.assertNotNull(restoredLyric);
        org.junit.jupiter.api.Assertions.assertEquals(1, Lyric.count());
        org.junit.jupiter.api.Assertions.assertEquals(1, Lyric.count("sourcePath", lrcPath.toRealPath().toString()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyrics/{id}", originalLyricId)
                .then()
                .statusCode(200)
                .body("contentHash", equalTo(sha256(secondContent)))
                .body("content", equalTo(secondContent));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/songs/{songId}/lyrics", songId)
                .then()
                .statusCode(200)
                .body("lyricStatus", equalTo("BOUND"))
                .body("lyricId", equalTo(originalLyricId.intValue()))
                .body("content", equalTo(secondContent));
    }

    @Test
    void deleteUnboundLyricRecordDoesNotDeleteSourceFile() throws IOException {
        Path lrcPath = lyricDir.resolve("周杰伦 - 搁浅.lrc");
        Files.writeString(lrcPath, "[ti:搁浅]\n[ar:周杰伦]\n");
        String sourcePath = lrcPath.toRealPath().toString();
        Lyric lyric = QuarkusTransaction.requiringNew().call(() -> {
            Lyric value = new Lyric();
            value.title = "搁浅";
            value.artist = "周杰伦";
            value.album = "七里香";
            value.sourceType = "LOCAL_FILE";
            value.sourcePath = sourcePath;
            value.content = "[ti:搁浅]\n[ar:周杰伦]\n";
            value.contentHash = "delete-unbound-hash";
            value.format = "LRC";
            value.parseStatus = "PARSED";
            value.persist();
            return value;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/lyrics/{id}", lyric.id)
                .then()
                .statusCode(200)
                .body("success", equalTo(true));

        org.junit.jupiter.api.Assertions.assertNull(Lyric.findById(lyric.id));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(lrcPath));
    }

    @Test
    void deleteBoundLyricRecordIsRejected() throws IOException {
        createSong("周杰伦 - 晴天.flac", "晴天", "周杰伦");
        Path lrcPath = lyricDir.resolve("周杰伦 - 晴天.lrc");
        Files.writeString(lrcPath, "[ti:晴天]\n[ar:周杰伦]\n[00:01.00]first\n");
        scanLyrics();
        Lyric lyric = Lyric.find("sourcePath", lrcPath.toRealPath().toString()).firstResult();

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/lyrics/{id}", lyric.id)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        org.junit.jupiter.api.Assertions.assertNotNull(Lyric.findById(lyric.id));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(lrcPath));
    }

    @Test
    void listLyricsSupportsUnboundAndFailedFilters() {
        Lyric lyric = createLyric("搁浅", "周杰伦", "七里香", "PARSE_FAILED");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyrics?bindStatus=UNBOUND&parseStatus=FAILED&keyword={keyword}", "搁浅")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(lyric.id.intValue()))
                .body("items[0].title", equalTo("搁浅"))
                .body("items[0].artist", equalTo("周杰伦"))
                .body("items[0].album", equalTo("七里香"))
                .body("items[0].parseStatus", equalTo("FAILED"))
                .body("items[0].bindStatus", equalTo("UNBOUND"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/lyrics/{id}", lyric.id)
                .then()
                .statusCode(200)
                .body("bindStatus", equalTo("UNBOUND"))
                .body("boundSong", equalTo(null))
                .body("boundSongs.size()", equalTo(0));
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

    private io.restassured.response.ValidatableResponse scanLyrics() {
        return given()
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
                .statusCode(200);
    }

    private Lyric createLyric(String title, String artist, String album, String parseStatus) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Lyric lyric = new Lyric();
            lyric.title = title;
            lyric.artist = artist;
            lyric.album = album;
            lyric.sourceType = "LOCAL_FILE";
            lyric.sourcePath = lyricDir.resolve(artist + " - " + title + ".lrc").toAbsolutePath().normalize().toString();
            lyric.content = "[ti:" + title + "]\n";
            lyric.contentHash = title + "-hash";
            lyric.format = "LRC";
            lyric.parseStatus = parseStatus;
            lyric.parseMessage = "read failed";
            lyric.persist();
            return lyric;
        });
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
