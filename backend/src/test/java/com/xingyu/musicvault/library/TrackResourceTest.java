package com.xingyu.musicvault.library;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class TrackResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";

    @BeforeEach
    @Transactional
    void cleanTracks() {
        Track.deleteAll();
    }

    @Test
    void createListGetUpdateAndDeleteTrack() {
        Integer id = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "  First Song  "
                        }
                        """)
                .when()
                .post("/api/tracks")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("title", equalTo("First Song"))
                .body("normalizedTitle", equalTo("first song"))
                .body("metadataStatus", equalTo("pending"))
                .extract()
                .path("id");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/tracks")
                .then()
                .statusCode(200)
                .body("$", hasSize(1))
                .body("[0].id", equalTo(id));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/tracks/{id}", id)
                .then()
                .statusCode(200)
                .body("title", equalTo("First Song"));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "Updated Song",
                          "metadataStatus": "matched"
                        }
                        """)
                .when()
                .put("/api/tracks/{id}", id)
                .then()
                .statusCode(200)
                .body("title", equalTo("Updated Song"))
                .body("metadataStatus", equalTo("matched"))
                .body("updatedAt.length()", greaterThan(0));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/tracks/{id}", id)
                .then()
                .statusCode(204);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/tracks/{id}", id)
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void rejectsBlankTitle() {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "   "
                        }
                        """)
                .when()
                .post("/api/tracks")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void rejectsInvalidStatus() {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "title": "First Song",
                          "metadataStatus": "done"
                        }
                        """)
                .when()
                .post("/api/tracks")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }
}
