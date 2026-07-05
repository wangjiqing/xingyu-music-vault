package com.xingyu.musicvault.search;

import com.xingyu.musicvault.settings.AppSetting;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;

@QuarkusTest
class BraveSearchResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";

    @BeforeEach
    @Transactional
    void clean() {
        AppSetting.deleteAll();
    }

    @Test
    void unconfiguredStatusAndSearchDoNotLeakKey() {
        given()
                .header("Authorization", AUTHORIZATION)
                .get("/api/admin/brave-search/status")
                .then()
                .statusCode(200)
                .body("configured", equalTo(false))
                .body("searchable", equalTo(false))
                .body("mode", equalTo("NONE"))
                .body("$", not(hasKey("apiKey")));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"query\":\"歌手 歌名 歌词\"}")
                .post("/api/admin/brave-search/search")
                .then()
                .statusCode(400);
    }

    @Test
    void pauseDisablesConsoleManagedSearchWithoutReturningKey() {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"apiKey\":\"test-brave-key\", \"updatedBy\":\"admin\"}")
                .post("/api/admin/brave-search/key")
                .then()
                .statusCode(200)
                .body("configured", equalTo(true))
                .body("searchable", equalTo(true))
                .body("mode", equalTo("CONSOLE"))
                .body("$", not(hasKey("apiKey")));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"enabled\":false, \"updatedBy\":\"admin\"}")
                .patch("/api/admin/brave-search/enabled")
                .then()
                .statusCode(200)
                .body("configured", equalTo(true))
                .body("enabled", equalTo(false))
                .body("searchable", equalTo(false))
                .body("message", equalTo("Brave 搜索已暂停"))
                .body("$", not(hasKey("apiKey")));
    }
}
