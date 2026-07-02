package com.xingyu.musicvault.alignment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@ApplicationScoped
public class LyricAlignmentJobWriter {
    private static final Logger LOG = Logger.getLogger(LyricAlignmentJobWriter.class);
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
        writeJsonAtomically(jobDir, REQUEST_FILE, requestSnapshot, writer);
        writeStringAtomically(jobDir, TRUSTED_LYRICS_FILE, trustedLyricsSnapshot);
        if (sections != null && !sections.isNull()) {
            writeJsonAtomically(jobDir, SECTIONS_FILE, sections, writer);
        }
    }

    public void markReady(Path jobDir) throws IOException {
        writeStringAtomically(jobDir, READY_FILE, "");
    }

    private void writeJsonAtomically(Path jobDir, String fileName, JsonNode json, ObjectWriter writer) throws IOException {
        Path tempFile = Files.createTempFile(jobDir, "." + fileName + ".", ".tmp");
        boolean moved = false;
        try {
            writer.writeValue(tempFile.toFile(), json);
            moveIntoPlace(tempFile, jobDir.resolve(fileName));
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private void writeStringAtomically(Path jobDir, String fileName, String content) throws IOException {
        Path tempFile = Files.createTempFile(jobDir, "." + fileName + ".", ".tmp");
        boolean moved = false;
        try {
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            moveIntoPlace(tempFile, jobDir.resolve(fileName));
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempFile);
            }
        }
    }

    private void moveIntoPlace(Path tempFile, Path target) throws IOException {
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            LOG.warnf(exception, "Atomic move is not supported for alignment job file; falling back to replace move: target=%s", target);
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
