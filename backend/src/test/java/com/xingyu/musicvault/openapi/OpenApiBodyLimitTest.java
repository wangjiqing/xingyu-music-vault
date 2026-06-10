package com.xingyu.musicvault.openapi;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
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
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(OpenApiBodyLimitTest.SmallBodyLimitProfile.class)
class OpenApiBodyLimitTest {
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
        credential.name = "body-limit-client";
        credential.accessKey = "xmv_ak_" + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8));
        credential.secretEncrypted = cryptoService.encryptSecret(SECRET);
        credential.secretFingerprint = cryptoService.fingerprint(SECRET);
        credential.scopesJson = "[\"OPENAPI_READ\",\"OPENAPI_WRITE\"]";
        credential.enabled = true;
        credential.persist();
        accessKey = credential.accessKey;
    }

    @Test
    void oversizedBodyIsRejectedBeforeSignatureVerificationConsumesIt() {
        byte[] body = "0123456789".getBytes(StandardCharsets.UTF_8);

        signed("POST", "/api/open/v1/body-hash-test", List.of(), body)
                .contentType("application/json")
                .body(body)
                .post("/api/open/v1/body-hash-test")
                .then()
                .statusCode(413)
                .body("code", equalTo("OPENAPI_PAYLOAD_TOO_LARGE"))
                .body("details.maxBodyBytes", equalTo(8));
    }

    private RequestSpecification signed(String method, String path, List<String> queryParts, byte[] body) {
        long timestamp = Instant.now().toEpochMilli();
        String nonce = UUID.randomUUID().toString();
        String canonicalPath = queryParts.isEmpty() ? path : path + "?" + String.join("&", queryParts);
        String canonical = String.join("\n",
                method,
                canonicalPath,
                HexFormat.of().formatHex(sha256(body)),
                String.valueOf(timestamp),
                nonce
        );
        return given()
                .header("X-Xingyu-Access-Key", accessKey)
                .header("X-Xingyu-Timestamp", String.valueOf(timestamp))
                .header("X-Xingyu-Nonce", nonce)
                .header("X-Xingyu-Signature-Version", "v1")
                .header("X-Xingyu-Signature", hmac(SECRET, canonical));
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

    public static class SmallBodyLimitProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "xingyu.openapi.hmac.max-body-bytes", "8"
            );
        }
    }
}
