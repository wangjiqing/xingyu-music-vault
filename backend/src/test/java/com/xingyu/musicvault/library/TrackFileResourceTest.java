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
                .body("items", hasSize(2))
                .body("page", equalTo(0))
                .body("size", equalTo(20))
                .body("total", equalTo(2));

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
                .body("items", hasSize(1))
                .body("items[0].id", equalTo(flacId.intValue()));
    }

    @Test
    void listTrackFilesSupportsPaginationExtAndKeywordFilters() {
        createTrackFile("/music/live/Alpha.flac", "Alpha.flac", "flac");
        createTrackFile("/music/studio/Beta.mp3", "Beta.mp3", "mp3");
        createTrackFile("/music/live/Gamma.flac", "Gamma.flac", "flac");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/track-files?page=0&size=1&ext=flac")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("page", equalTo(0))
                .body("size", equalTo(1))
                .body("total", equalTo(2));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/track-files?keyword=beta")
                .then()
                .statusCode(200)
                .body("items", hasSize(1))
                .body("items[0].fileName", equalTo("Beta.mp3"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/track-files?page=-1")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
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
