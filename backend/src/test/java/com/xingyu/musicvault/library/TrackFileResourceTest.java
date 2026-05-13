package com.xingyu.musicvault.library;

import com.xingyu.musicvault.job.ScanJob;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TrackFileResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";

    @BeforeEach
    @Transactional
    void cleanData() {
        TrackFile.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void listGetAndFilterTrackFiles() {
        Long flacId = createTrackFile("/tmp/a.flac", "a.flac", "flac");
        createTrackFile("/tmp/b.mp3", "b.mp3", "mp3");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/track-files")
                .then()
                .statusCode(200)
                .body("$", hasSize(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/track-files/{id}", flacId)
                .then()
                .statusCode(200)
                .body("id", equalTo(flacId.intValue()))
                .body("fileName", equalTo("a.flac"))
                .body("fileExt", equalTo("flac"))
                .body("createdAt", notNullValue());

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/track-files?ext=flac")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(flacId.intValue()));
    }

    @Test
    void missingTrackFileReturnsNotFound() {
        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/track-files/999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void requiresToken() {
        given()
                .when()
                .get("/api/track-files")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Transactional
    Long createTrackFile(String filePath, String fileName, String fileExt) {
        TrackFile trackFile = new TrackFile();
        trackFile.filePath = filePath;
        trackFile.fileName = fileName;
        trackFile.fileExt = fileExt;
        trackFile.fileSize = 10;
        trackFile.lastModifiedAt = LocalDateTime.now();
        trackFile.persist();
        return trackFile.id;
    }
}
