package com.xingyu.musicvault.job;

import com.xingyu.musicvault.library.TrackFile;
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

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        musicDir = Files.createTempDirectory("music-vault-resource-test");
        TrackFile.deleteAll();
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
                .body("$", hasSize(1))
                .body("[0].id", equalTo(id));
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
}
