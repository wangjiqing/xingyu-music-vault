package com.xingyu.musicvault.search;

import com.sun.net.httpserver.HttpServer;
import com.xingyu.musicvault.settings.AppSetting;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@TestProfile(BraveSearchErrorResourceTest.LocalBraveProfile.class)
class BraveSearchErrorResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static HttpServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 18082), 0);
        server.createContext("/brave", exchange -> {
            exchange.getResponseHeaders().add("Retry-After", "30");
            byte[] body = "{\"error\":\"rate limited\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    @Transactional
    void clean() {
        AppSetting.deleteAll();
    }

    @Test
    void rateLimitReturnsClearServiceUnavailableMessage() {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"apiKey\":\"console-key\", \"updatedBy\":\"admin\"}")
                .post("/api/admin/brave-search/key")
                .then()
                .statusCode(200);

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{\"query\":\"歌手 歌名 歌词\", \"count\": 1}")
                .post("/api/admin/brave-search/search")
                .then()
                .statusCode(503)
                .body("message", containsString("请求过于频繁"));

        given()
                .header("Authorization", AUTHORIZATION)
                .get("/api/admin/brave-search/status")
                .then()
                .statusCode(200)
                .body("lastError", equalTo("Brave Search 请求过于频繁，请在 30 秒后再试"));
    }

    public static class LocalBraveProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("music-vault.brave-search-api-url", "http://127.0.0.1:18082/brave");
        }
    }
}
