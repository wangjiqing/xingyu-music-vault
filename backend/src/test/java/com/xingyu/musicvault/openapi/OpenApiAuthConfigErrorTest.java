package com.xingyu.musicvault.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(OpenApiAuthConfigErrorTest.AuthMissingTokenProfile.class)
class OpenApiAuthConfigErrorTest {
    @Test
    void missingMasterKeyReturnsConfigError() {
        given()
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(500)
                .body("code", equalTo("OPENAPI_CONFIG_ERROR"))
                .body("message", equalTo("OpenAPI credential master key is not configured"))
                .body("traceId", notNullValue())
                .body("details", notNullValue());
    }

    public static class AuthMissingTokenProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "xingyu.openapi.auth.enabled", "true",
                    "xingyu.openapi.credential.master-key", "",
                    "xingyu.openapi.rate-limit.enabled", "false"
            );
        }
    }
}
