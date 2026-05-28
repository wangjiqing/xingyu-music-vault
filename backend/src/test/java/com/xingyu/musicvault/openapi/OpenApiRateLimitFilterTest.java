package com.xingyu.musicvault.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(OpenApiRateLimitFilterTest.RateLimitEnabledProfile.class)
class OpenApiRateLimitFilterTest {
    @Inject
    OpenApiRateLimiter rateLimiter;

    @BeforeEach
    void resetLimiter() {
        rateLimiter.reset();
    }

    @Test
    void rateLimitEnabledReturnsTooManyRequestsAfterLimit() {
        hitFromForwardedFor("10.0.0.1", 200);
        hitFromForwardedFor("10.0.0.1", 200);

        given()
                .header("X-Forwarded-For", "10.0.0.1")
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(429)
                .body("code", equalTo("OPENAPI_RATE_LIMITED"))
                .body("message", equalTo("Too many OpenAPI requests"))
                .body("traceId", notNullValue())
                .body("details.limit", equalTo(2))
                .body("details.windowSeconds", equalTo(60));
    }

    @Test
    void xForwardedForUsesFirstIpOnly() {
        hitFromForwardedFor("10.0.0.2, 10.0.0.99", 200);
        hitFromForwardedFor("10.0.0.2, 10.0.0.88", 200);
        hitFromForwardedFor("10.0.0.2, 10.0.0.77", 429);
        hitFromForwardedFor("10.0.0.3, 10.0.0.2", 200);
    }

    @Test
    void xRealIpIsUsedWhenForwardedForIsMissing() {
        hitFromRealIp("10.0.0.4", 200);
        hitFromRealIp("10.0.0.4", 200);
        hitFromRealIp("10.0.0.4", 429);
    }

    private void hitFromForwardedFor(String value, int status) {
        given()
                .header("X-Forwarded-For", value)
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(status);
    }

    private void hitFromRealIp(String value, int status) {
        given()
                .header("X-Real-IP", value)
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(status);
    }

    public static class RateLimitEnabledProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "xingyu.openapi.auth.enabled", "false",
                    "xingyu.openapi.rate-limit.enabled", "true",
                    "xingyu.openapi.rate-limit.requests-per-minute", "2"
            );
        }
    }
}
