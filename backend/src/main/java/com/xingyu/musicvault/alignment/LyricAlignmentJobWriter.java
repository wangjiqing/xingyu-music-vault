package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@ApplicationScoped
public class LyricAlignmentJobWriter {
    private static final String REQUEST_FILE = "request.json";
    private static final String TRUSTED_LYRICS_FILE = "trusted-lyrics.txt";
    private static final String SECTIONS_FILE = "sections.json";
    private static final String READY_FILE = "READY";

    @Inject
    ObjectMapper objectMapper;

    public void writeInputs(Path jobDir, JsonNode requestSnapshot, String trustedLyricsSnapshot, JsonNode sections)
            throws IOException {
        ObjectWriter writer = objectMapper.writerWithDefaultPrettyPrinter();
        Files.createDirectory(jobDir);
        writer.writeValue(jobDir.resolve(REQUEST_FILE).toFile(), requestSnapshot);
        Files.writeString(jobDir.resolve(TRUSTED_LYRICS_FILE), trustedLyricsSnapshot, StandardCharsets.UTF_8);
        if (sections != null && !sections.isNull()) {
            writer.writeValue(jobDir.resolve(SECTIONS_FILE).toFile(), sections);
        }
    }

    public void markReady(Path jobDir) throws IOException {
        Files.writeString(jobDir.resolve(READY_FILE), "", StandardCharsets.UTF_8);
    }
}
