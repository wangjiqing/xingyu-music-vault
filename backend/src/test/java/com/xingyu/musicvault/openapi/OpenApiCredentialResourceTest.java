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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

@QuarkusTest
@TestProfile(OpenApiCredentialResourceTest.StrictAdminSessionProfile.class)
class OpenApiCredentialResourceTest {
    @Inject
    AdminSessionService sessionService;

    @BeforeEach
    @Transactional
    void clean() {
        OpenApiRequestNonce.deleteAll();
        OpenApiCredential.deleteAll();
        AdminUser.deleteAll();
        sessionService.clearAll();
    }

    @Test
    void credentialManagementRequiresAdminSessionAndDoesNotLeakSecret() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"client","scopes":["OPENAPI_READ"]}
                        """)
                .post("/api/admin/openapi/credentials")
                .then()
                .statusCode(401);

        setupAdmin();
        Cookie cookie = login();

        var created = given()
                .cookie(cookie)
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"client","description":"phone","scopes":["OPENAPI_READ"],"expiresAt":null}
                        """)
                .post("/api/admin/openapi/credentials")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("accessKey", notNullValue())
                .body("secretKey", notNullValue())
                .body("secretFingerprint", notNullValue())
                .extract()
                .response();
        Integer id = created.path("id");
        String accessKey = created.path("accessKey");
        String secretKey = created.path("secretKey");

        signed(accessKey, secretKey)
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(200);

        given()
                .cookie(cookie)
                .get("/api/admin/openapi/credentials")
                .then()
                .statusCode(200)
                .body("[0].id", equalTo(id))
                .body("[0].secretKey", nullValue())
                .body("[0].secretEncrypted", nullValue())
                .body("[0]", not(hasKey("secretKey")))
                .body("[0]", not(hasKey("secretEncrypted")))
                .body("[0].scopes[0]", equalTo("OPENAPI_READ"));

        given()
                .cookie(cookie)
                .contentType(ContentType.JSON)
                .body("{\"enabled\":false}")
                .patch("/api/admin/openapi/credentials/{id}/enabled", id)
                .then()
                .statusCode(200)
                .body("enabled", equalTo(false));

        given()
                .cookie(cookie)
                .contentType(ContentType.JSON)
                .patch("/api/admin/openapi/credentials/{id}/enabled", id)
                .then()
                .statusCode(400);

        signed(accessKey, secretKey)
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401);

        given()
                .cookie(cookie)
                .delete("/api/admin/openapi/credentials/{id}", id)
                .then()
                .statusCode(204);

        signed(accessKey, secretKey)
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401);
    }

    @Test
    void invalidScopesAreRejected() {
        setupAdmin();
        Cookie cookie = login();

        given()
                .cookie(cookie)
                .contentType(ContentType.JSON)
                .body("""
                        {"name":"client","scopes":["NOPE"]}
                        """)
                .post("/api/admin/openapi/credentials")
                .then()
                .statusCode(400);
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

    private io.restassured.specification.RequestSpecification signed(String accessKey, String secretKey) {
        long timestamp = Instant.now().toEpochMilli();
        String nonce = UUID.randomUUID().toString();
        String canonical = String.join("\n",
                "GET",
                "/api/open/v1/server/info",
                HexFormat.of().formatHex(sha256(new byte[0])),
                String.valueOf(timestamp),
                nonce
        );
        return given()
                .header("X-Xingyu-Access-Key", accessKey)
                .header("X-Xingyu-Timestamp", String.valueOf(timestamp))
                .header("X-Xingyu-Nonce", nonce)
                .header("X-Xingyu-Signature-Version", "v1")
                .header("X-Xingyu-Signature", hmac(secretKey, canonical));
    }

    private String hmac(String secret, String canonical) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
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
