package com.xingyu.musicvault.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class OpenApiAuthFilterTest {
    private static final String SECRET = "xmv_sk_test_secret";

    String accessKey;

    @Inject
    OpenApiCredentialCryptoService cryptoService;

    @BeforeEach
    @Transactional
    void cleanCredentials() {
        OpenApiRequestNonce.deleteAll();
        OpenApiCredential.deleteAll();

        OpenApiCredential credential = new OpenApiCredential();
        credential.name = "test-client";
        credential.accessKey = "xmv_ak_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        credential.secretEncrypted = cryptoService.encryptSecret(SECRET);
        credential.secretFingerprint = cryptoService.fingerprint(SECRET);
        credential.scopesJson = "[\"OPENAPI_READ\"]";
        credential.enabled = true;
        credential.persist();
        accessKey = credential.accessKey;
    }

    @Test
    void missingHeadersReturnUnauthorized() {
        given()
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_UNAUTHORIZED"))
                .body("traceId", notNullValue());
    }

    @Test
    void legacyBearerAndXingyuTokenAreRejected() {
        given()
                .header("Authorization", "Bearer change-me")
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401);

        given()
                .header("X-Xingyu-Api-Token", "change-me")
                .when()
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401);
    }

    @Test
    void hmacFilterDoesNotInterceptAdminOpenApiCredentialPath() {
        given()
                .when()
                .get("/api/admin/openapi/credentials")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void rejectsInvalidAuthenticationInputs() {
        signed("GET", "/api/open/v1/server/info", List.of(), accessKey, SECRET, "v2", Instant.now().toEpochMilli(), UUID.randomUUID().toString())
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401);

        signed("GET", "/api/open/v1/server/info", List.of(), "missing", SECRET, "v1", Instant.now().toEpochMilli(), UUID.randomUUID().toString())
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_UNAUTHORIZED"));

        signed("GET", "/api/open/v1/server/info", List.of(), accessKey, "wrong-secret", "v1", Instant.now().toEpochMilli(), UUID.randomUUID().toString())
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401);

        signed("GET", "/api/open/v1/server/info", List.of(), accessKey, SECRET, "v1", Instant.now().minusSeconds(600).toEpochMilli(), UUID.randomUUID().toString())
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401);
    }

    @Test
    void disabledAndExpiredCredentialsReturnSpecificErrorCodes() {
        QuarkusTransaction.requiringNew().run(() -> {
            OpenApiCredential credential = OpenApiCredential.find("accessKey", accessKey).firstResult();
            credential.enabled = false;
        });

        signed("GET", "/api/open/v1/server/info", List.of(), accessKey, SECRET, "v1", Instant.now().toEpochMilli(), UUID.randomUUID().toString())
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_CREDENTIAL_DISABLED"));

        QuarkusTransaction.requiringNew().run(() -> {
            OpenApiCredential credential = OpenApiCredential.find("accessKey", accessKey).firstResult();
            credential.enabled = true;
            credential.expiresAt = LocalDateTime.now().minusMinutes(1);
        });

        signed("GET", "/api/open/v1/server/info", List.of(), accessKey, SECRET, "v1", Instant.now().toEpochMilli(), UUID.randomUUID().toString())
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_CREDENTIAL_EXPIRED"));
    }

    @Test
    void validSignatureAllowsReadAndRepeatingNonceIsRejected() {
        String nonce = UUID.randomUUID().toString();
        RequestSpecification request = signed("GET", "/api/open/v1/server/info", List.of(), accessKey, SECRET, "v1", Instant.now().toEpochMilli(), nonce);

        request.get("/api/open/v1/server/info")
                .then()
                .statusCode(200)
                .body("apiVersion", equalTo("v1"));

        signed("GET", "/api/open/v1/server/info", List.of(), accessKey, SECRET, "v1", Instant.now().toEpochMilli(), nonce)
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(401)
                .body("message", equalTo("Repeated OpenAPI nonce"));
    }

    @Test
    void postRequestBodyHashMustMatchRawBodyBytes() {
        QuarkusTransaction.requiringNew().run(() -> {
            OpenApiCredential credential = OpenApiCredential.find("accessKey", accessKey).firstResult();
            credential.scopesJson = "[\"OPENAPI_READ\",\"OPENAPI_WRITE\"]";
        });
        byte[] body = "{\"hello\":\"星语\"}".getBytes(StandardCharsets.UTF_8);

        signed("POST", "/api/open/v1/body-hash-test", List.of(), accessKey, SECRET, "v1", Instant.now().toEpochMilli(), UUID.randomUUID().toString(), body)
                .contentType("application/json")
                .body(body)
                .post("/api/open/v1/body-hash-test")
                .then()
                .statusCode(200)
                .body("length", equalTo(new String(body, StandardCharsets.UTF_8).length()));

        signed("POST", "/api/open/v1/body-hash-test", List.of(), accessKey, SECRET, "v1", Instant.now().toEpochMilli(), UUID.randomUUID().toString(), "different".getBytes(StandardCharsets.UTF_8))
                .contentType("application/json")
                .body(body)
                .post("/api/open/v1/body-hash-test")
                .then()
                .statusCode(401)
                .body("code", equalTo("OPENAPI_UNAUTHORIZED"));
    }

    @Test
    void missingReadScopeReturnsForbidden() {
        QuarkusTransaction.requiringNew().run(() -> {
            OpenApiCredential credential = OpenApiCredential.find("accessKey", accessKey).firstResult();
            credential.scopesJson = "[\"OPENAPI_WRITE\"]";
        });

        signed("GET", "/api/open/v1/server/info", List.of(), accessKey, SECRET, "v1", Instant.now().toEpochMilli(), UUID.randomUUID().toString())
                .get("/api/open/v1/server/info")
                .then()
                .statusCode(403)
                .body("code", equalTo("OPENAPI_FORBIDDEN"));
    }

    private RequestSpecification signed(String method, String path, List<String> queryParts, String ak, String sk, String version, long timestamp, String nonce) {
        return signed(method, path, queryParts, ak, sk, version, timestamp, nonce, new byte[0]);
    }

    private RequestSpecification signed(String method, String path, List<String> queryParts, String ak, String sk, String version, long timestamp, String nonce, byte[] body) {
        String canonicalPath = queryParts.isEmpty() ? path : path + "?" + String.join("&", queryParts);
        String canonical = String.join("\n",
                method,
                canonicalPath,
                HexFormat.of().formatHex(sha256(body)),
                String.valueOf(timestamp),
                nonce
        );
        return given()
                .header("X-Xingyu-Access-Key", ak)
                .header("X-Xingyu-Timestamp", String.valueOf(timestamp))
                .header("X-Xingyu-Nonce", nonce)
                .header("X-Xingyu-Signature-Version", version)
                .header("X-Xingyu-Signature", hmac(sk, canonical));
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
}
