package com.xingyu.musicvault.auth;

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
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(AdminAuthResourceTest.StrictAdminSessionProfile.class)
class AdminAuthResourceTest {
    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin-password";

    @Inject
    AdminSessionService sessionService;

    @BeforeEach
    @Transactional
    void cleanAuthState() {
        AdminUser.deleteAll();
        sessionService.clearAll();
    }

    @Test
    void setupStatusIsFalseBeforeAdminExists() {
        given()
                .when()
                .get("/api/admin/auth/setup-status")
                .then()
                .statusCode(200)
                .body("initialized", equalTo(false));
    }

    @Test
    void setupCreatesAdminAndClosesSetupEntry() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "admin",
                          "password": "admin-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/setup")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("username", equalTo(USERNAME))
                .body("role", equalTo(AdminUser.ROLE_ADMIN))
                .body("passwordHash", nullValue());

        given()
                .when()
                .get("/api/admin/auth/setup-status")
                .then()
                .statusCode(200)
                .body("initialized", equalTo(true));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "second-admin",
                          "password": "second-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/setup")
                .then()
                .statusCode(409)
                .body("error", equalTo("conflict"));

        assertPasswordIsStoredAsHash();
    }

    @Test
    void setupRejectsBlankUsernameAndShortPassword() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": " ",
                          "password": "admin-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/setup")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "admin",
                          "password": "short"
                        }
                        """)
                .when()
                .post("/api/admin/auth/setup")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void loginCreatesHttpOnlySessionCookieAndMeReturnsUser() {
        setupAdmin();

        Cookie sessionCookie = given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "admin",
                          "password": "admin-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/login")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("username", equalTo(USERNAME))
                .body("role", equalTo(AdminUser.ROLE_ADMIN))
                .body("passwordHash", nullValue())
                .extract()
                .detailedCookie(AdminSessionService.COOKIE_NAME);

        assertTrue(sessionCookie.isHttpOnly());
        assertFalse(sessionCookie.getValue().isBlank());
        assertTrue(sessionCookie.getPath() == null || "/".equals(sessionCookie.getPath()));

        given()
                .cookie(sessionCookie)
                .when()
                .get("/api/admin/auth/me")
                .then()
                .statusCode(200)
                .body("username", equalTo(USERNAME))
                .body("role", equalTo(AdminUser.ROLE_ADMIN))
                .body("passwordHash", nullValue());
    }

    @Test
    void loginFailsWithWrongPassword() {
        setupAdmin();

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "admin",
                          "password": "wrong-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/login")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void meRequiresLoginAndLogoutInvalidatesSession() {
        setupAdmin();

        given()
                .when()
                .get("/api/admin/auth/me")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));

        Cookie sessionCookie = login();

        given()
                .cookie(sessionCookie)
                .when()
                .post("/api/admin/auth/logout")
                .then()
                .statusCode(200);

        given()
                .cookie(sessionCookie)
                .when()
                .get("/api/admin/auth/me")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void protectedAdminApiRequiresSessionAndAllowsLoggedInUser() {
        setupAdmin();

        given()
                .when()
                .get("/api/music/stats")
                .then()
                .statusCode(401)
                .body("message", equalTo("未登录或登录已过期"));

        Cookie sessionCookie = login();

        given()
                .cookie(sessionCookie)
                .when()
                .get("/api/music/stats")
                .then()
                .statusCode(200)
                .body("total", notNullValue());

        given()
                .when()
                .get("/api/admin/server/info")
                .then()
                .statusCode(401)
                .body("message", equalTo("未登录或登录已过期"));

        given()
                .cookie(sessionCookie)
                .when()
                .get("/api/admin/server/info")
                .then()
                .statusCode(200)
                .body("serviceName", equalTo("xingyu-music-vault"))
                .body("serviceVersion", equalTo("1.3.1"));
    }

    @Test
    void openApiStaysSeparateAndRegisterEndpointDoesNotExist() {
        setupAdmin();

        given()
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_UNAUTHORIZED"));

        given()
                .header("X-Xingyu-Api-Token", "not-admin-session")
                .when()
                .get("/api/music/stats")
                .then()
                .statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "admin2",
                          "password": "admin-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/register")
                .then()
                .statusCode(404);
    }

    private Cookie login() {
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "admin",
                          "password": "admin-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .detailedCookie(AdminSessionService.COOKIE_NAME);
    }

    private void setupAdmin() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "username": "admin",
                          "password": "admin-password"
                        }
                        """)
                .when()
                .post("/api/admin/auth/setup")
                .then()
                .statusCode(201);
    }

    @Transactional
    void assertPasswordIsStoredAsHash() {
        AdminUser user = AdminUser.find("username", USERNAME).firstResult();
        assertFalse(PASSWORD.equals(user.passwordHash));
        assertTrue(user.passwordHash.startsWith("pbkdf2_sha256$"));
    }

    public static class StrictAdminSessionProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "xingyu.admin.auth.test-legacy-token.enabled", "false",
                    "xingyu.openapi.auth.enabled", "false"
            );
        }
    }
}
