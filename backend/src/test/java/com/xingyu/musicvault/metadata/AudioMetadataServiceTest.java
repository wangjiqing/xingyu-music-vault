package com.xingyu.musicvault.metadata;

import com.xingyu.musicvault.metadata.MetadataDtos.MetadataSnapshot;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class AudioMetadataServiceTest {
    Path musicDir;

    @Inject
    AudioMetadataService audioMetadataService;

    @BeforeEach
    void setUp() throws IOException {
        musicDir = Files.createTempDirectory(Path.of("target/test-music"), "audio-metadata-");
    }

    @Test
    void readsEmbeddedTagSuccessfully() throws Exception {
        Path file = musicDir.resolve("tagged.mp3");
        createMp3(file,
                "-metadata", "title=Tag Title",
                "-metadata", "artist=Tag Artist",
                "-metadata", "album=Tag Album",
                "-metadata", "album_artist=Tag Album Artist",
                "-metadata", "date=2024",
                "-metadata", "genre=Pop",
                "-metadata", "track=7/12");

        MetadataSnapshot snapshot = audioMetadataService.read(file);

        assertEquals("Tag Title", snapshot.title());
        assertEquals("Tag Artist", snapshot.artist());
        assertEquals("Tag Album", snapshot.album());
        assertEquals("Tag Album Artist", snapshot.albumArtist());
        assertEquals(2024, snapshot.year());
        assertEquals("Pop", snapshot.genre());
        assertEquals(7, snapshot.trackNumber());
        assertNotNull(snapshot.duration());
    }

    @Test
    void emptyTagReturnsEmptySnapshot() throws Exception {
        Path file = musicDir.resolve("empty.mp3");
        createMp3(file, "-map_metadata", "-1");

        MetadataSnapshot snapshot = audioMetadataService.read(file);

        assertNull(snapshot.title());
        assertNull(snapshot.artist());
        assertNull(snapshot.album());
        assertNull(snapshot.albumArtist());
        assertNull(snapshot.year());
        assertNull(snapshot.genre());
        assertNull(snapshot.trackNumber());
        assertNotNull(snapshot.duration());
    }

    @Test
    void missingFileReturnsClearError() {
        AudioMetadataException exception = assertThrows(
                AudioMetadataException.class,
                () -> audioMetadataService.read(musicDir.resolve("missing.mp3"))
        );

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("does not exist"));
    }

    private void createMp3(Path path, String... metadataArgs) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("ffmpeg");
        command.add("-y");
        command.add("-f");
        command.add("lavfi");
        command.add("-i");
        command.add("anullsrc=r=44100:cl=mono");
        command.add("-t");
        command.add("0.1");
        command.add("-q:a");
        command.add("9");
        command.addAll(List.of(metadataArgs));
        command.add(path.toString());
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("ffmpeg failed: " + output);
        }
    }
}
