package com.xingyu.musicvault.openapi;

import com.xingyu.musicvault.auth.AdminSessionService;
import com.xingyu.musicvault.auth.AdminUser;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import io.restassured.http.Cookie;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@TestProfile(OpenApiCredentialConfigErrorTest.MissingMasterKeyProfile.class)
class OpenApiCredentialConfigErrorTest {
    @Inject
    AdminSessionService sessionService;

    @BeforeEach
    @Transactional
    void clean() {
        OpenApiCredential.deleteAll();
        AdminUser.deleteAll();
        sessionService.clearAll();
    }

    @Test
    void createCredentialWithoutMasterKeyReturnsConfigError() {
        setupAdmin();
        Cookie cookie = login();

        given()
                .cookie(cookie)
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"client","scopes":["OPENAPI_READ","OPENAPI_WRITE"]}
                        """)
                .post("/api/admin/openapi/credentials")
                .then()
                .statusCode(500)
                .body("code", equalTo("OPENAPI_CONFIG_ERROR"))
                .body("message", equalTo("OpenAPI credential master key is not configured"))
                .body("traceId", notNullValue())
                .body("details", notNullValue());
    }

    private void setupAdmin() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"admin-password"}
                        """)
                .post("/api/admin/auth/setup")
                .then()
                .statusCode(201);
    }

    private Cookie login() {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {"username":"admin","password":"admin-password"}
                        """)
                .post("/api/admin/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .detailedCookie(AdminSessionService.COOKIE_NAME);
    }

    public static class MissingMasterKeyProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "xingyu.admin.auth.test-legacy-token.enabled", "false",
                    "xingyu.openapi.credential.master-key", ""
            );
        }
    }
}
