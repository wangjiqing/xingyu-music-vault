package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LyricWorkerEventsReaderTest {

    @TempDir
    Path tempDir;

    LyricWorkerEventsReader reader;

    @BeforeEach
    void setUp() {
        reader = new LyricWorkerEventsReader();
        reader.objectMapper = new ObjectMapper();
    }

    @Test
    void returnsOnlyRecentFiftyEvents() throws Exception {
        StringBuilder jsonl = new StringBuilder();
        for (int index = 1; index <= 60; index++) {
            jsonl.append("""
                    {"eventId":"event-%d","timestamp":"2026-07-08T10:00:00Z","level":"INFO","type":"STAGE","stage":"TRANSCRIBING","message":"event %d"}
                    """.formatted(index, index));
        }
        Files.writeString(tempDir.resolve("events.jsonl"), jsonl.toString());

        LyricWorkerEventsReader.EventsSnapshot snapshot = reader.read(tempDir);

        assertTrue(snapshot.available());
        assertFalse(snapshot.truncated());
        assertNull(snapshot.readError());
        assertEquals(50, snapshot.events().size());
        assertEquals("event-11", snapshot.events().getFirst().eventId());
        assertEquals("event-60", snapshot.events().getLast().eventId());
    }

    @Test
    void emptyEventsFileReturnsAvailableEmptySnapshot() throws Exception {
        Files.writeString(tempDir.resolve("events.jsonl"), "");

        LyricWorkerEventsReader.EventsSnapshot snapshot = reader.read(tempDir);

        assertTrue(snapshot.available());
        assertFalse(snapshot.truncated());
        assertNull(snapshot.readError());
        assertTrue(snapshot.events().isEmpty());
    }

    @Test
    void malformedEventLineDoesNotFailWholeSnapshot() throws Exception {
        Files.writeString(tempDir.resolve("events.jsonl"), """
                {"eventId":"event-1","message":"ok"}
                {
                {"eventId":"event-2","message":"also ok"}
                """);

        LyricWorkerEventsReader.EventsSnapshot snapshot = reader.read(tempDir);

        assertTrue(snapshot.available());
        assertEquals("Some worker event lines are not readable", snapshot.readError());
        assertEquals(2, snapshot.events().size());
        assertEquals("event-1", snapshot.events().getFirst().eventId());
        assertEquals("event-2", snapshot.events().getLast().eventId());
    }

    @Test
    void largeEventsFileStopsAtConfiguredLineWindow() throws Exception {
        StringBuilder jsonl = new StringBuilder();
        for (int index = 1; index <= 5100; index++) {
            jsonl.append("""
                    {"eventId":"event-%d","message":"event %d"}
                    """.formatted(index, index));
        }
        Files.writeString(tempDir.resolve("events.jsonl"), jsonl.toString());

        LyricWorkerEventsReader.EventsSnapshot snapshot = reader.read(tempDir);

        assertTrue(snapshot.available());
        assertTrue(snapshot.truncated());
        assertEquals(50, snapshot.events().size());
        assertEquals("event-4951", snapshot.events().getFirst().eventId());
        assertEquals("event-5000", snapshot.events().getLast().eventId());
    }
}
