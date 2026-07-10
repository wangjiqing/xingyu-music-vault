package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xingyu.musicvault.alignment.LyricAlignmentDtos.WorkerEventResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@ApplicationScoped
public class LyricWorkerEventsReader {
    private static final Logger LOG = Logger.getLogger(LyricWorkerEventsReader.class);
    private static final String EVENTS_JSONL = "events.jsonl";
    private static final int DEFAULT_LIMIT = 50;
    private static final long MAX_BYTES = 1024 * 1024;
    private static final int MAX_LINES = 5000;

    @Inject
    ObjectMapper objectMapper;

    public EventsSnapshot read(Path jobDir) {
        if (jobDir == null || !Files.isDirectory(jobDir)) {
            return new EventsSnapshot(false, false, "Worker job directory is missing", List.of());
        }
        Path eventsPath = jobDir.resolve(EVENTS_JSONL).normalize();
        if (!eventsPath.startsWith(jobDir) || !Files.isRegularFile(eventsPath)) {
            return new EventsSnapshot(false, false, null, List.of());
        }
        try {
            EventLines eventLines = readRecentLines(eventsPath);
            List<String> lines = eventLines.lines();
            List<WorkerEventResponse> parsed = new ArrayList<>();
            String readError = null;
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                try {
                    JsonNode event = objectMapper.readTree(line);
                    parsed.add(toResponse(event));
                } catch (IOException | RuntimeException exception) {
                    LOG.warnf(exception, "Skipping malformed worker event JSONL line: path=%s", eventsPath);
                    readError = "Some worker event lines are not readable";
                }
            }
            return new EventsSnapshot(true, eventLines.truncated(), readError, parsed);
        } catch (IOException | RuntimeException exception) {
            LOG.warnf(exception, "Failed to read worker events JSONL: path=%s", eventsPath);
            return new EventsSnapshot(true, false, "Worker events are not readable yet", List.of());
        }
    }

    private EventLines readRecentLines(Path eventsPath) throws IOException {
        Deque<String> recentLines = new ArrayDeque<>(DEFAULT_LIMIT);
        boolean truncated = false;
        int linesRead = 0;
        long bytesRead = 0;
        try (BufferedReader reader = Files.newBufferedReader(eventsPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                bytesRead += line.getBytes(StandardCharsets.UTF_8).length + 1L;
                if (bytesRead > MAX_BYTES) {
                    truncated = true;
                    break;
                }
                linesRead++;
                if (linesRead > MAX_LINES) {
                    truncated = true;
                    break;
                }
                if (recentLines.size() == DEFAULT_LIMIT) {
                    recentLines.removeFirst();
                }
                recentLines.addLast(line);
            }
        }
        return new EventLines(List.copyOf(recentLines), truncated);
    }

    private WorkerEventResponse toResponse(JsonNode event) {
        return new WorkerEventResponse(
                firstText(event, "eventId", "id"),
                firstText(event, "timestamp", "time", "createdAt"),
                text(event, "level"),
                firstText(event, "type", "eventType"),
                text(event, "stage"),
                text(event, "message"),
                objectOrArray(event.get("details"))
        );
    }

    private String firstText(JsonNode event, String... names) {
        for (String name : names) {
            String value = text(event, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String text(JsonNode event, String name) {
        JsonNode node = event == null ? null : event.get(name);
        return node != null && node.isTextual() && !node.asText().isBlank() ? node.asText() : null;
    }

    private JsonNode objectOrArray(JsonNode node) {
        return node != null && (node.isObject() || node.isArray()) ? node : null;
    }

    public record EventsSnapshot(
            boolean available,
            boolean truncated,
            String readError,
            List<WorkerEventResponse> events
    ) {
    }

    private record EventLines(List<String> lines, boolean truncated) {
    }
}
