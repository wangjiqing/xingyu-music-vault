package com.xingyu.musicvault.search;

import com.xingyu.musicvault.settings.AppSetting;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(BraveSearchEnvModeResourceTest.EnvBraveProfile.class)
class BraveSearchEnvModeResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";

    @BeforeEach
    @Transactional
    void clean() {
        AppSetting.deleteAll();
    }

    @Test
    void envModeRejectsConsoleKeyAndPauseMutations() {
        given()
                .header("Authorization", AUTHORIZATION)
                .get("/api/admin/brave-search/status")
                .then()
                .statusCode(200)
                .body("mode", equalTo("ENV"))
                .body("searchable", equalTo(true));

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"apiKey\":\"console-key\", \"updatedBy\":\"admin\"}")
                .post("/api/admin/brave-search/key")
                .then()
                .statusCode(400);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"enabled\":false, \"updatedBy\":\"admin\"}")
                .patch("/api/admin/brave-search/enabled")
                .then()
                .statusCode(400);

        given()
                .header("Authorization", AUTHORIZATION)
                .get("/api/admin/brave-search/status")
                .then()
                .statusCode(200)
                .body("mode", equalTo("ENV"))
                .body("searchable", equalTo(true));
    }

    public static class EnvBraveProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("music-vault.brave-search-api-key", "env-brave-key");
        }
    }
}
