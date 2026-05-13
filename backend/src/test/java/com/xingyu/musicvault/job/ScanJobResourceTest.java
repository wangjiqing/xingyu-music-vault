package com.xingyu.musicvault.job;

import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.library.Track;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ScanJobResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ALLOWED_MUSIC_ROOT = Path.of("target/test-music");

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ALLOWED_MUSIC_ROOT);
        musicDir = Files.createTempDirectory(ALLOWED_MUSIC_ROOT, "resource-test-");
        TrackFile.deleteAll();
        Track.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void createGetAndRunScanJob() throws IOException {
        Files.createDirectories(musicDir.resolve("nested"));
        Files.writeString(musicDir.resolve("a.flac"), "");
        Files.writeString(musicDir.resolve("nested").resolve("b.mp3"), "");
        Files.writeString(musicDir.resolve("c.txt"), "");

        Integer id = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "jobType": "library_scan",
                          "musicDirs": ["%s"]
                        }
                        """.formatted(musicDir))
                .when()
                .post("/api/scan-jobs")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("status", equalTo("pending"))
                .body("musicDirs", hasSize(1))
                .extract()
                .path("id");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/scan-jobs/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("pending"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/scan-jobs/{id}/run", id)
                .then()
                .statusCode(200)
                .body("status", equalTo("completed"))
                .body("totalFiles", equalTo(3))
                .body("scannedFiles", equalTo(2))
                .body("newFiles", equalTo(2))
                .body("updatedFiles", equalTo(0))
                .body("skippedFiles", equalTo(1))
                .body("errorFiles", equalTo(0));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/scan-jobs")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(id))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("total", equalTo(1));
    }

    @Test
    void listScanJobsSupportsPaginationAndStatusFilter() {
        Long pendingId = createScanJob("pending");
        createScanJob("completed");
        createScanJob("failed");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/scan-jobs?page=0&size=2&status=pending")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(pendingId.intValue()))
                .body("items[0].status", equalTo("pending"))
                .body("page", equalTo(0))
                .body("size", equalTo(2))
                .body("total", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/scan-jobs?status=done")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void repeatedRunReturnsConflictForRunningAndCompletedJobs() {
        Long runningId = createScanJob("running");
        Long completedId = createScanJob("completed");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/scan-jobs/{id}/run", runningId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .post("/api/scan-jobs/{id}/run", completedId)
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));
    }

    @Test
    void requiresToken() {
        given()
                .when()
                .get("/api/scan-jobs")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void missingScanJobReturnsNotFound() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/scan-jobs/999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Transactional
    Long createScanJob(String status) {
        ScanJob scanJob = new ScanJob();
        scanJob.jobType = "library_scan";
        scanJob.status = status;
        scanJob.musicDirs = musicDir.toString();
        scanJob.persist();
        return scanJob.id;
    }
}
