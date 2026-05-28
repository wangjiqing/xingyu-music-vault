package com.xingyu.musicvault.openapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiAccessLogFormatterTest {
    @Test
    void accessLogLineDoesNotContainTokens() {
        OpenApiAccessLogFormatter formatter = new OpenApiAccessLogFormatter();

        String line = formatter.format(
                "GET",
                "/api/open/v1/tracks",
                200,
                18,
                "127.0.0.1",
                "trace-1"
        );

        assertTrue(line.contains("OpenAPI access method=GET"));
        assertTrue(line.contains("path=/api/open/v1/tracks"));
        assertTrue(line.contains("traceId=trace-1"));
        assertFalse(line.contains("Authorization"));
        assertFalse(line.contains("X-Xingyu-Api-Token"));
        assertFalse(line.contains("Bearer"));
    }

    @Test
    void accessLogLineReplacesAsciiControlCharacters() {
        OpenApiAccessLogFormatter formatter = new OpenApiAccessLogFormatter();

        String line = formatter.format(
                "G\0ET",
                "/api/open/v1/tracks\nmeta",
                200,
                18,
                "127.0.0.1\b",
                "trace\t1"
        );

        assertTrue(line.contains("method=G_ET"));
        assertTrue(line.contains("path=/api/open/v1/tracks_meta"));
        assertTrue(line.contains("clientIp=127.0.0.1_"));
        assertTrue(line.contains("traceId=trace_1"));
        assertFalse(line.contains("\0"));
        assertFalse(line.contains("\b"));
        assertFalse(line.contains("\n"));
        assertFalse(line.contains("\t"));
    }
}
