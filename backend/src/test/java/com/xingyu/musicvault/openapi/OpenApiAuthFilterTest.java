package com.xingyu.musicvault.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.junit.QuarkusTestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(OpenApiAuthFilterTest.AuthEnabledProfile.class)
class OpenApiAuthFilterTest {
    private static final String OPENAPI_TOKEN = "openapi-secret";
    private static final String ADMIN_AUTHORIZATION = "Bearer change-me";

    @Test
    void authEnabledWithoutTokenReturnsUnauthorized() {
        given()
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_UNAUTHORIZED"))
                .body("message", equalTo("Missing or invalid OpenAPI token"))
                .body("traceId", notNullValue())
                .body("details", notNullValue());
    }

    @Test
    void authEnabledAcceptsBearerToken() {
        given()
                .header("Authorization", "Bearer " + OPENAPI_TOKEN)
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(200)
                .body("apiVersion", equalTo("v1"));
    }

    @Test
    void authEnabledAcceptsXingyuTokenHeader() {
        given()
                .header("X-Xingyu-Api-Token", OPENAPI_TOKEN)
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(200)
                .body("apiVersion", equalTo("v1"));
    }

    @Test
    void authEnabledRejectsInvalidToken() {
        given()
                .header("Authorization", "Bearer wrong-token")
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_UNAUTHORIZED"));
    }

    @Test
    void openApiAuthDoesNotAffectAdminApi() {
        given()
                .header("Authorization", ADMIN_AUTHORIZATION)
                .when()
                .get("/api/music/999999")
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    public static class AuthEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "xingyu.openapi.auth.enabled", "true",
                    "xingyu.openapi.auth.token", OPENAPI_TOKEN,
                    "xingyu.openapi.rate-limit.enabled", "false"
            );
        }
    }
}
