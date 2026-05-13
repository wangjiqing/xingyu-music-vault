package com.xingyu.musicvault.library;

import com.xingyu.musicvault.job.ScanJob;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class MusicResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ALLOWED_MUSIC_ROOT = Path.of("target/test-music");

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ALLOWED_MUSIC_ROOT);
        musicDir = Files.createTempDirectory(ALLOWED_MUSIC_ROOT, "music-api-test-");
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void scanListAndGetMusic() throws IOException, InterruptedException {
        Files.writeString(musicDir.resolve("周杰伦 - 晴天.flac"), "first");
        Files.writeString(musicDir.resolve("note.txt"), "skip");

        Integer scanJobId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(musicDir))
                .when()
                .post("/api/music/scan")
                .then()
                .statusCode(202)
                .body("accepted", equalTo(true))
                .body("scanJobId", notNullValue())
                .body("message", equalTo("Scan accepted"))
                .extract()
                .path("scanJobId");

        waitForScanJobStatus(scanJobId.longValue(), "completed");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].title", equalTo("晴天"))
                .body("items[0].artist", equalTo("周杰伦"))
                .body("items[0].fileExtension", equalTo("flac"))
                .body("items[0].fileSize", equalTo(5))
                .body("items[0].lastModifiedTime", notNullValue())
                .body("total", equalTo(1));

        Integer musicId = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music")
                .then()
                .statusCode(200)
                .extract()
                .path("items[0].id");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{id}", musicId)
                .then()
                .statusCode(200)
                .body("id", equalTo(musicId))
                .body("title", equalTo("晴天"))
                .body("artist", equalTo("周杰伦"));
    }

    @Test
    void scanAcceptsMissingDirectoryAndMarksJobFailed() throws InterruptedException {
        Integer scanJobId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(musicDir.resolve("missing")))
                .when()
                .post("/api/music/scan")
                .then()
                .statusCode(202)
                .body("accepted", equalTo(true))
                .body("scanJobId", notNullValue())
                .extract()
                .path("scanJobId");

        ScanJob scanJob = waitForScanJobStatus(scanJobId.longValue(), "failed");
        assertNotNull(scanJob.errorMessage);
    }

    @Test
    void missingMusicReturnsNotFound() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    private ScanJob waitForScanJobStatus(Long scanJobId, String expectedStatus) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            ScanJob scanJob = ScanJob.findById(scanJobId);
            if (scanJob != null && expectedStatus.equals(scanJob.status)) {
                assertEquals(expectedStatus, scanJob.status);
                return scanJob;
            }
            Thread.sleep(100);
        }
        fail("Scan job did not reach status: " + expectedStatus);
        return null;
    }
}
