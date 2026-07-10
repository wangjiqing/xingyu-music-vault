package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricAlignmentWorkerStatusReaderTest {

    @TempDir
    Path tempDir;

    LyricAlignmentWorkerStatusReader reader;

    @BeforeEach
    void setUp() {
        reader = new LyricAlignmentWorkerStatusReader();
        reader.objectMapper = new ObjectMapper();
    }

    @Test
    void readsRunningStatusWithoutHeartbeatAndEmptyErrorMessage() throws Exception {
        Files.writeString(tempDir.resolve("status.json"), """
                {
                  "statusSchemaVersion": 1,
                  "requestSchemaVersion": 3,
                  "jobId": "job-001",
                  "taskType": "LYRIC_DRAFT_EXTRACTION",
                  "state": "RUNNING",
                  "stage": "TRANSCRIBING",
                  "error": {"code": "ASR_FAILED", "message": ""},
                  "warnings": []
                }
                """);

        LyricAlignmentWorkerStatusReader.WorkerStatusSnapshot snapshot = reader.read(tempDir);

        assertTrue(snapshot.statusAvailable());
        assertEquals(1, snapshot.statusSchemaVersion());
        assertEquals(3, snapshot.requestSchemaVersion());
        assertEquals("RUNNING", snapshot.status());
        assertEquals("TRANSCRIBING", snapshot.stage());
        assertNull(snapshot.heartbeatAt());
        assertNotNull(snapshot.error());
        assertEquals("ASR_FAILED", snapshot.error().path("code").asText());
        assertEquals("", snapshot.error().path("message").asText());
    }

    @Test
    void ignoresNonObjectConfigNodes() throws Exception {
        Files.writeString(tempDir.resolve("status.json"), """
                {
                  "statusSchemaVersion": 1,
                  "state": "RUNNING",
                  "requestedConfig": "bad",
                  "resolvedConfig": 123
                }
                """);

        LyricAlignmentWorkerStatusReader.WorkerStatusSnapshot snapshot = reader.read(tempDir);

        assertTrue(snapshot.statusAvailable());
        assertNull(snapshot.requestedConfig());
        assertNull(snapshot.resolvedConfig());
    }
}
