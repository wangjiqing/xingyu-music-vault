package com.xingyu.musicvault.openapi;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

class OpenApiTestClient {
    private static final byte[] EMPTY_BODY = new byte[0];
    private static final String SECRET = "xmv_sk_test_secret";

    private final OpenApiCredentialCryptoService cryptoService;
    private final String accessKey;
    private final String secret;

    private OpenApiTestClient(OpenApiCredentialCryptoService cryptoService, String accessKey, String secret) {
        this.cryptoService = cryptoService;
        this.accessKey = accessKey;
        this.secret = secret;
    }

    static OpenApiTestClient create(OpenApiCredentialCryptoService cryptoService, List<OpenApiScope> scopes) {
        String accessKey = "xmv_ak_test_" + UUID.randomUUID();
        OpenApiCredential credential = new OpenApiCredential();
        credential.name = "OpenAPI test credential";
        credential.accessKey = accessKey;
        credential.secretEncrypted = cryptoService.encryptSecret(SECRET);
        credential.secretFingerprint = cryptoService.fingerprint(SECRET);
        credential.scopesJson = scopesJson(scopes);
        credential.enabled = true;
        credential.persist();
        return new OpenApiTestClient(cryptoService, accessKey, SECRET);
    }

    RequestSpecification get(String path) {
        return signed("GET", path, EMPTY_BODY);
    }

    RequestSpecification get(String path, Object... queryPairs) {
        RequestSpecification request = signed("GET", pathWithCanonicalQuery(path, queryPairs), EMPTY_BODY);
        for (int i = 0; i < queryPairs.length; i += 2) {
            request.queryParam(String.valueOf(queryPairs[i]), queryPairs[i + 1]);
        }
        return request;
    }

    RequestSpecification signed(String method, String pathWithQuery, byte[] body) {
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String nonce = UUID.randomUUID().toString();
        String canonical = String.join("\n",
                method.toUpperCase(Locale.ROOT),
                canonicalPathWithQuery(pathWithQuery),
                sha256Hex(body == null ? EMPTY_BODY : body),
                timestamp,
                nonce
        );
        return RestAssured.given()
                .header("X-Xingyu-Access-Key", accessKey)
                .header("X-Xingyu-Timestamp", timestamp)
                .header("X-Xingyu-Nonce", nonce)
                .header("X-Xingyu-Signature-Version", "v1")
                .header("X-Xingyu-Signature", cryptoService.hmacSha256Hex(secret, canonical));
    }

    private static String scopesJson(List<OpenApiScope> scopes) {
        return scopes.stream()
                .map(OpenApiScope::name)
                .map(scope -> "\"" + scope + "\"")
                .reduce((left, right) -> left + "," + right)
                .map(value -> "[" + value + "]")
                .orElse("[]");
    }

    private static String pathWithCanonicalQuery(String path, Object... queryPairs) {
        if (queryPairs.length % 2 != 0) {
            throw new IllegalArgumentException("queryPairs must be name/value pairs");
        }
        List<QueryPart> parts = new ArrayList<>();
        for (int i = 0; i < queryPairs.length; i += 2) {
            parts.add(new QueryPart(String.valueOf(queryPairs[i]), String.valueOf(queryPairs[i + 1])));
        }
        String query = parts.stream()
                .sorted(Comparator.comparing(QueryPart::name).thenComparing(QueryPart::value))
                .map(part -> encode(part.name()) + "=" + encode(part.value()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return query.isBlank() ? path : path + "?" + query;
    }

    private static String canonicalPathWithQuery(String pathWithQuery) {
        int queryStart = pathWithQuery.indexOf('?');
        String path = queryStart < 0 ? pathWithQuery : pathWithQuery.substring(0, queryStart);
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (queryStart < 0 || queryStart == pathWithQuery.length() - 1) {
            return path;
        }
        List<QueryPart> parts = new ArrayList<>();
        for (String rawPart : pathWithQuery.substring(queryStart + 1).split("&")) {
            if (rawPart.isBlank()) {
                continue;
            }
            String[] keyValue = rawPart.split("=", 2);
            String name = decode(keyValue[0]);
            String value = keyValue.length == 2 ? decode(keyValue[1]) : "";
            parts.add(new QueryPart(name, value));
        }
        String query = parts.stream()
                .sorted(Comparator.comparing(QueryPart::name).thenComparing(QueryPart::value))
                .map(part -> encode(part.name()) + "=" + encode(part.value()))
                .reduce((left, right) -> left + "&" + right)
                .orElse("");
        return query.isBlank() ? path : path + "?" + query;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private record QueryPart(String name, String value) {
    }
}
