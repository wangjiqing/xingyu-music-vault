package com.xingyu.musicvault.openapi;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.artwork.ArtworkService;
import com.xingyu.musicvault.artwork.MusicArtworkBinding;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.lyrics.Lyric;
import com.xingyu.musicvault.lyrics.SongLyric;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class OpenApiResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ROOT = Path.of("target/test-music/open-api");

    Path workDir;

    @Inject
    OpenApiChangeLogService changeLogService;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ROOT);
        workDir = Files.createTempDirectory(ROOT, "case-");
        OpenApiSyncChangeLog.deleteAll();
        OpenApiLibraryState state = OpenApiLibraryState.findById(1);
        if (state != null) {
            state.libraryVersion = 1;
            state.lastChangedAt = null;
        }
        MusicArtworkBinding.deleteAll();
        Artwork.deleteAll();
        SongLyric.deleteAll();
        Lyric.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
    }

    @Test
    void serverInfoAndSyncStateExposeReadOnlyCapabilitiesAndCounts() {
        Long trackId = createTrack("晴天.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop", 242_000L);
        createTrack("七里香.flac", "七里香", "周杰伦", "七里香", 2004, "Pop", 280_000L);
        changeLogService.recordTrackChange(trackId, "updated", List.of("metadata"));

        given()
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(200)
                .body("serviceVersion", equalTo("1.0.0"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(200)
                .body("serviceVersion", equalTo("1.0.0"))
                .body("apiVersion", equalTo("v1"))
                .body("readOnly", equalTo(true))
                .body("features.tracks", equalTo(true))
                .body("features.scanTrigger", equalTo(false));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/sync/state")
                .then()
                .statusCode(200)
                .body("libraryVersion", equalTo(2))
                .body("trackCount", equalTo(2))
                .body("artistCount", equalTo(1))
                .body("albumCount", equalTo(2))
                .body("changesAvailable", equalTo(true))
                .body("lastChangedAt", notNullValue());
    }

    @Test
    void rateLimitDisabledDoesNotLimitOpenApiRequests() {
        for (int i = 0; i < 5; i++) {
            given()
                    .when()
                    .get("/api/open/v1/server/info")
                    .then()
                    .statusCode(200);
        }
    }

    @Test
    void syncStateCountsOnlyActiveTrackBindings() throws IOException {
        Long activeTrackId = createTrack("活跃.flac", "活跃", "星语", "Demo", 2026, "Pop", 180_000L);
        Long deletedTrackId = createTrack("已删除.flac", "已删除", "星语", "Demo", 2026, "Pop", 180_000L);
        bindLyric(activeTrackId, "活跃", "星语", "Demo", "LRC", "[00:01.00]active");
        bindLyric(deletedTrackId, "已删除", "星语", "Demo", "LRC", "[00:01.00]deleted");
        bindArtwork(activeTrackId, "active.png");
        bindArtwork(deletedTrackId, "deleted.png");
        markTrackDeleted(deletedTrackId);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/sync/state")
                .then()
                .statusCode(200)
                .body("trackCount", equalTo(1))
                .body("lyricsCount", equalTo(1))
                .body("artworkCount", equalTo(1))
                .body("changesAvailable", equalTo(true));
    }

    @Test
    void orphanLyricBindingDoesNotAdvertiseAvailableResourceOrInflateSyncState() {
        Long trackId = createTrack("orphan-lyric.flac", "Orphan Lyric", "星语", "Contract", 2026, "Pop", 180_000L);
        bindLyric(trackId, "Orphan Lyric", "星语", "Contract", "LRC", "[00:01.00]orphan");
        deleteLyricByTitle("Orphan Lyric");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/sync/state")
                .then()
                .statusCode(200)
                .body("trackCount", equalTo(1))
                .body("lyricsCount", equalTo(0));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("keyword", "Orphan Lyric")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("items[0].lyricsAvailable", equalTo(false))
                .body("items[0].lyricsStatus", equalTo("NO_LYRIC"))
                .body("items[0].lyricId", nullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", trackId)
                .then()
                .statusCode(200)
                .body("available", equalTo(false))
                .body("lyricId", nullValue());
    }

    @Test
    void tracksSupportPagingKeywordFiltersAndPathSafeDetail() {
        Long firstId = createTrack("周杰伦 - 晴天.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop", 242_000L);
        createTrack("孙燕姿 - 遇见.flac", "遇见", "孙燕姿", "The Moment", 2003, "Mandopop", 210_000L);
        createTrack("周杰伦 - 七里香.flac", "七里香", "周杰伦", "七里香", 2004, "Pop", 280_000L);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks?page=0&pageSize=2")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("page", equalTo(0))
                .body("pageSize", equalTo(2))
                .body("total", equalTo(3));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("keyword", "晴")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].title", equalTo("晴天"));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("artist", "周杰伦")
                .queryParam("album", "叶惠美")
                .queryParam("year", 2003)
                .queryParam("genre", "Pop")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(firstId.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks?pageSize=101")
                .then()
                .statusCode(400)
                .body("code", equalTo("OPENAPI_INVALID_ARGUMENT"))
                .body("traceId", notNullValue())
                .body("details", notNullValue());

        given()
                .when()
                .get("/api/open/v1/tracks?sort=sideways")
                .then()
                .statusCode(400)
                .body("code", equalTo("OPENAPI_UNSUPPORTED_SORT"))
                .body("traceId", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}", firstId)
                .then()
                .statusCode(200)
                .body("id", equalTo(firstId.intValue()))
                .body("fileName", equalTo("周杰伦 - 晴天.flac"))
                .body("filePath", nullValue())
                .body("sourcePath", nullValue());
    }

    @Test
    void lyricsArtworkAggregatesAndMatchAreAvailable() throws IOException {
        Long qingtianId = createTrack("周杰伦 - 晴天.flac", "晴天", "周杰伦", "叶惠美", 2003, "Pop", 242_000L);
        Long qlxId = createTrack("周杰伦 - 七里香.flac", "七里香", "周杰伦", "七里香", 2004, "Pop", 280_000L);
        bindLyric(qingtianId, "晴天", "周杰伦", "叶惠美", "LRC", "[ti:晴天]\n[00:01.00]故事的小黄花\n");
        bindArtwork(qingtianId, "qingtian.png");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics", qingtianId)
                .then()
                .statusCode(200)
                .body("trackId", equalTo(qingtianId.intValue()))
                .body("format", equalTo("LRC"))
                .body("content", equalTo("[ti:晴天]\n[00:01.00]故事的小黄花\n"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", qingtianId)
                .then()
                .statusCode(200)
                .body("available", equalTo(true))
                .body("hash", startsWith("sha256:"))
                .body("etag", startsWith("\"lyrics-" + qingtianId + "-"))
                .body("updatedAt", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork", qingtianId)
                .then()
                .statusCode(200)
                .contentType("image/png")
                .header("Cache-Control", notNullValue())
                .header("ETag", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", qingtianId)
                .then()
                .statusCode(200)
                .body("available", equalTo(true))
                .body("mimeType", equalTo("image/png"))
                .body("hash", startsWith("sha256:"))
                .body("etag", startsWith("\"artwork-" + qingtianId + "-"))
                .body("width", equalTo(3))
                .body("height", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/artists")
                .then()
                .statusCode(200)
                .body("[0].artistName", equalTo("周杰伦"))
                .body("[0].trackCount", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/artists/{artistName}/tracks", "周杰伦")
                .then()
                .statusCode(200)
                .body("total", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/albums")
                .then()
                .statusCode(200)
                .body("album", hasItem("叶惠美"))
                .body("album", hasItem("七里香"));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("album", "七里香")
                .when()
                .get("/api/open/v1/albums/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(qlxId.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("title", "晴天")
                .queryParam("artist", "周杰伦")
                .queryParam("album", "叶惠美")
                .queryParam("durationMs", 244_000)
                .when()
                .get("/api/open/v1/match/track")
                .then()
                .statusCode(200)
                .body("matched", equalTo(true))
                .body("score", equalTo(100))
                .body("track.id", equalTo(qingtianId.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("title", "不存在的歌")
                .when()
                .get("/api/open/v1/match/track")
                .then()
                .statusCode(200)
                .body("matched", equalTo(false))
                .body("track", nullValue());
    }

    @Test
    void trackAvailableFlagsStayConsistentWithLyricsAndArtworkMeta() throws IOException {
        Long readyId = createTrack("ready.flac", "Ready", "星语", "Contract", 2026, "Pop", 180_000L);
        Long emptyId = createTrack("empty.flac", "Empty", "星语", "Contract", 2026, "Pop", 181_000L);
        bindLyric(readyId, "Ready", "星语", "Contract", "LRC", "[00:01.00]ready");
        bindArtwork(readyId, "ready.png");

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("keyword", "Ready")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("items[0].lyricsAvailable", equalTo(true))
                .body("items[0].lyricsStatus", equalTo("BOUND"))
                .body("items[0].lyricId", notNullValue())
                .body("items[0].artworkAvailable", equalTo(true))
                .body("items[0].artworkStatus", equalTo("BOUND"))
                .body("items[0].artworkId", notNullValue())
                .body("items[0].artworkUrl", equalTo("/api/open/v1/tracks/" + readyId + "/artwork"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", readyId)
                .then()
                .statusCode(200)
                .body("available", equalTo(true));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", readyId)
                .then()
                .statusCode(200)
                .body("available", equalTo(true));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("keyword", "Empty")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("items[0].id", equalTo(emptyId.intValue()))
                .body("items[0].lyricsAvailable", equalTo(false))
                .body("items[0].lyricsStatus", equalTo("NO_LYRIC"))
                .body("items[0].lyricId", nullValue())
                .body("items[0].artworkAvailable", equalTo(false))
                .body("items[0].artworkStatus", equalTo("MISSING"))
                .body("items[0].artworkId", nullValue())
                .body("items[0].artworkUrl", nullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", emptyId)
                .then()
                .statusCode(200)
                .body("available", equalTo(false))
                .body("lyricId", nullValue())
                .body("hash", nullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", emptyId)
                .then()
                .statusCode(200)
                .body("available", equalTo(false))
                .body("artworkId", nullValue())
                .body("hash", nullValue());
    }

    @Test
    void missingArtworkFileDoesNotAdvertiseAvailableResource() throws IOException {
        Long trackId = createTrack("missing-cover.flac", "Missing Cover", "星语", "Contract", 2026, "Pop", 180_000L);
        Path imagePath = bindArtwork(trackId, "missing-cover.png");
        Files.delete(imagePath);

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("keyword", "Missing Cover")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("items[0].id", equalTo(trackId.intValue()))
                .body("items[0].artworkAvailable", equalTo(false))
                .body("items[0].artworkStatus", equalTo("MISSING"))
                .body("items[0].artworkId", nullValue())
                .body("items[0].artworkUrl", nullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", trackId)
                .then()
                .statusCode(200)
                .body("available", equalTo(false))
                .body("artworkId", nullValue())
                .body("etag", nullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork", trackId)
                .then()
                .statusCode(404)
                .body("code", equalTo("OPENAPI_ARTWORK_NOT_FOUND"));
    }

    @Test
    void controlPlaneMetadataChangesAreVisibleInOpenApiDetailAndList() {
        Long trackId = createTrack("metadata.flac", "Before", "Old Artist", "Old Album", 2025, "Pop", 180_000L);
        updateTrackMetadata(trackId, "After", "New Artist", "New Album", 2026, "Folk");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}", trackId)
                .then()
                .statusCode(200)
                .body("title", equalTo("After"))
                .body("artist", equalTo("New Artist"))
                .body("album", equalTo("New Album"))
                .body("year", equalTo(2026))
                .body("genre", equalTo("Folk"));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("keyword", "After")
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(trackId.intValue()))
                .body("items[0].artist", equalTo("New Artist"))
                .body("items[0].album", equalTo("New Album"));
    }

    @Test
    void syncChangesReturnIncrementalOrderedVersionsAndRespectLimit() {
        Long firstId = createTrack("first.flac", "First", "星语", "Demo", 2026, "Pop", 180_000L);
        Long secondId = createTrack("second.flac", "Second", "星语", "Demo", 2026, "Pop", 181_000L);

        long v2 = changeLogService.recordTrackChange(firstId, "created", List.of("metadata")).version;
        long v3 = changeLogService.recordLyricsChange(firstId).version;
        long v4 = changeLogService.recordArtworkChange(secondId).version;

        assertTrue(v2 < v3);
        assertTrue(v3 < v4);

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("sinceVersion", 0)
                .queryParam("limit", 2)
                .when()
                .get("/api/open/v1/sync/changes")
                .then()
                .statusCode(200)
                .body("fromVersion", equalTo(0))
                .body("toVersion", equalTo((int) v4))
                .body("hasMore", equalTo(true))
                .body("items", hasSize(2))
                .body("items[0].version", equalTo((int) v2))
                .body("items[1].version", equalTo((int) v3));

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("sinceVersion", v2)
                .when()
                .get("/api/open/v1/sync/changes")
                .then()
                .statusCode(200)
                .body("items", hasSize(2))
                .body("items[0].version", equalTo((int) v3))
                .body("items[0].changedFields[0]", equalTo("lyrics"))
                .body("items[1].version", equalTo((int) v4))
                .body("hasMore", equalTo(false));
    }

    @Test
    void tracksUpdatedAfterUsesStrictGreaterThanSemantics() {
        Long trackId = createTrack("strict.flac", "Strict", "星语", "Demo", 2026, "Pop", 180_000L);
        String updatedAt = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}", trackId)
                .then()
                .statusCode(200)
                .extract()
                .path("updatedAt");

        given()
                .header("Authorization", AUTHORIZATION)
                .queryParam("updatedAfter", updatedAt)
                .when()
                .get("/api/open/v1/tracks")
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    @Test
    void lyricsHashAndEtagSupportConditionalRequests() {
        Long trackId = createTrack("歌词.flac", "歌词", "星语", "Demo", 2026, "Pop", 180_000L);
        bindLyric(trackId, "歌词", "星语", "Demo", "LRC", "[ti:歌词]\n[00:01.00]first\n");

        String hash = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", trackId)
                .then()
                .statusCode(200)
                .body("hash", startsWith("sha256:"))
                .body("etag", startsWith("\"lyrics-" + trackId + "-"))
                .extract()
                .path("hash");
        String etag = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", trackId)
                .then()
                .statusCode(200)
                .extract()
                .path("etag");

        given()
                .header("Authorization", AUTHORIZATION)
                .header("If-None-Match", etag)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics", trackId)
                .then()
                .statusCode(304)
                .header("ETag", equalTo(etag));

        given()
                .header("Authorization", AUTHORIZATION)
                .header("If-None-Match", "\"lyrics-stale\"")
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics", trackId)
                .then()
                .statusCode(200)
                .header("ETag", equalTo(etag))
                .body("hash", equalTo(hash));

        updateLyricContent("歌词", "[ti:歌词]\n[00:01.00]second\n");
        String changedHash = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics/meta", trackId)
                .then()
                .statusCode(200)
                .extract()
                .path("hash");
        assertNotEquals(hash, changedHash);
    }

    @Test
    void artworkHashAndEtagSupportConditionalRequests() throws IOException {
        Long trackId = createTrack("封面.flac", "封面", "星语", "Demo", 2026, "Pop", 180_000L);
        Path imagePath = bindArtwork(trackId, "cover.png");

        String hash = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", trackId)
                .then()
                .statusCode(200)
                .body("hash", startsWith("sha256:"))
                .body("etag", startsWith("\"artwork-" + trackId + "-"))
                .extract()
                .path("hash");
        String etag = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", trackId)
                .then()
                .statusCode(200)
                .extract()
                .path("etag");

        given()
                .header("Authorization", AUTHORIZATION)
                .header("If-None-Match", etag)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork", trackId)
                .then()
                .statusCode(304)
                .header("ETag", equalTo(etag));

        given()
                .header("Authorization", AUTHORIZATION)
                .header("If-None-Match", "\"artwork-stale\"")
                .when()
                .get("/api/open/v1/tracks/{id}/artwork", trackId)
                .then()
                .statusCode(200)
                .header("ETag", equalTo(etag))
                .contentType("image/png");

        writePng(imagePath, 5, 4);
        String changedHash = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork/meta", trackId)
                .then()
                .statusCode(200)
                .extract()
                .path("hash");
        assertNotEquals(hash, changedHash);
    }

    @Test
    void lyricsBodyAndArtworkFileReturnSpecificNotFoundErrors() throws IOException {
        Long trackId = createTrack("无资源.flac", "无资源", "星语", "Demo", 2026, "Pop", 180_000L);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/lyrics", trackId)
                .then()
                .statusCode(404)
                .body("code", equalTo("OPENAPI_LYRICS_NOT_FOUND"))
                .body("traceId", notNullValue());

        Path outsideRoot = Path.of("target/open-api-outside");
        Files.createDirectories(outsideRoot);
        Path outsideArtwork = outsideRoot.resolve("outside.png");
        writePng(outsideArtwork, 3, 2);
        bindArtworkPath(trackId, outsideArtwork);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/{id}/artwork", trackId)
                .then()
                .statusCode(404)
                .body("code", equalTo("OPENAPI_ARTWORK_NOT_FOUND"))
                .body("traceId", notNullValue());
    }

    @Test
    void missingTrackReturnsUnifiedOpenApiErrorAndAdminApiStillUsesExistingShape() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/open/v1/tracks/999999")
                .then()
                .statusCode(404)
                .body("code", equalTo("OPENAPI_TRACK_NOT_FOUND"))
                .body("message", equalTo("Track not found"))
                .body("traceId", notNullValue())
                .body("details", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    private Long createTrack(String fileName, String title, String artist, String album, Integer year, String genre, Long duration) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.album = album;
            track.albumArtist = artist;
            track.year = year;
            track.genre = genre;
            track.duration = duration;
            track.metadataStatus = "synced";
            track.lyricsStatus = "pending";
            track.artworkStatus = "missing";
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = workDir.resolve(fileName).toAbsolutePath().normalize().toString();
            trackFile.fileName = fileName;
            trackFile.fileExt = "flac";
            trackFile.fileSize = 5;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private void bindLyric(Long trackId, String title, String artist, String album, String format, String content) {
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = new Lyric();
            lyric.title = title;
            lyric.artist = artist;
            lyric.album = album;
            lyric.sourceType = "LOCAL_FILE";
            lyric.sourcePath = workDir.resolve(title + ".lrc").toAbsolutePath().normalize().toString();
            lyric.format = format;
            lyric.content = content;
            lyric.contentHash = title + "-hash";
            lyric.parseStatus = "PARSED";
            lyric.persist();

            SongLyric binding = new SongLyric();
            binding.songId = trackId;
            binding.lyricId = lyric.id;
            binding.matchType = "TITLE_ARTIST";
            binding.matchScore = 100;
            binding.isPrimary = true;
            binding.persist();
        });
    }

    private Path bindArtwork(Long trackId, String fileName) throws IOException {
        Path imagePath = workDir.resolve(fileName);
        writePng(imagePath, 3, 2);
        bindArtworkPath(trackId, imagePath);
        return imagePath;
    }

    private void updateLyricContent(String title, String content) {
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = Lyric.find("title", title).firstResult();
            lyric.content = content;
        });
    }

    private void deleteLyricByTitle(String title) {
        QuarkusTransaction.requiringNew().run(() -> {
            Lyric lyric = Lyric.find("title", title).firstResult();
            lyric.delete();
        });
    }

    private void updateTrackMetadata(Long trackFileId, String title, String artist, String album, Integer year, String genre) {
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(trackFileId);
            Track track = Track.findById(trackFile.trackId);
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.album = album;
            track.albumArtist = artist;
            track.year = year;
            track.genre = genre;
        });
    }

    private void markTrackDeleted(Long trackId) {
        QuarkusTransaction.requiringNew().run(() -> {
            TrackFile trackFile = TrackFile.findById(trackId);
            trackFile.deleteStatus = "trashed";
            trackFile.deletedAt = LocalDateTime.now();
        });
    }

    private void bindArtworkPath(Long trackId, Path imagePath) {
        QuarkusTransaction.requiringNew().run(() -> {
            Artwork artwork = new Artwork();
            artwork.filePath = imagePath.toAbsolutePath().normalize().toString();
            artwork.fileName = imagePath.getFileName().toString();
            artwork.fileExt = "png";
            artwork.mimeType = "image/png";
            artwork.fileSize = imageSize(imagePath);
            artwork.width = 3;
            artwork.height = 2;
            artwork.hash = "artwork-hash-" + imagePath.getFileName();
            artwork.sourceType = "local";
            artwork.sourcePath = artwork.filePath;
            artwork.title = "晴天";
            artwork.persist();

            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = trackId;
            binding.artworkId = artwork.id;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        });
    }

    private void writePng(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private long imageSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
