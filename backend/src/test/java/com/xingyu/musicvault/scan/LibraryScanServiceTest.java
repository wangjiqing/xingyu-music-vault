package com.xingyu.musicvault.scan;

import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.TrackFile;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
class LibraryScanServiceTest {
    @Inject
    LibraryScanService libraryScanService;

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        musicDir = Files.createTempDirectory("music-vault-service-test");
        TrackFile.deleteAll();
        ScanJob.deleteAll();
    }

    @Test
    void scansAudioFilesAndSkipsOthers() throws IOException {
        Files.writeString(musicDir.resolve("a.flac"), "first");
        Files.writeString(musicDir.resolve("b.mp3"), "second");
        Files.writeString(musicDir.resolve("c.txt"), "skip");

        Long scanJobId = createScanJob(musicDir);
        ScanJob scanJob = libraryScanService.run(scanJobId);

        assertEquals("completed", scanJob.status);
        assertEquals(3, scanJob.totalFiles);
        assertEquals(2, scanJob.scannedFiles);
        assertEquals(2, scanJob.newFiles);
        assertEquals(0, scanJob.updatedFiles);
        assertEquals(1, scanJob.skippedFiles);
        assertEquals(0, scanJob.errorFiles);
        assertNull(scanJob.errorMessage);
        assertNotNull(scanJob.startedAt);
        assertNotNull(scanJob.finishedAt);
        assertEquals(2, TrackFile.count());
    }

    @Test
    void repeatedScanUpdatesExistingTrackFiles() throws IOException {
        Path file = musicDir.resolve("a.flac");
        Files.writeString(file, "first");

        Long firstJobId = createScanJob(musicDir);
        libraryScanService.run(firstJobId);

        Files.writeString(file, "updated content");

        Long secondJobId = createScanJob(musicDir);
        ScanJob secondJob = libraryScanService.run(secondJobId);

        assertEquals("completed", secondJob.status);
        assertEquals(1, secondJob.totalFiles);
        assertEquals(1, secondJob.scannedFiles);
        assertEquals(0, secondJob.newFiles);
        assertEquals(1, secondJob.updatedFiles);
        assertEquals(1, TrackFile.count());

        TrackFile trackFile = TrackFile.find("fileName", "a.flac").firstResult();
        assertNotNull(trackFile);
        assertEquals(secondJobId, trackFile.scanJobId);
        assertEquals(Files.size(file), trackFile.fileSize);
    }

    @Test
    void missingDirectoryCompletesWithClearMessage() {
        Path missingDir = musicDir.resolve("missing");

        Long scanJobId = createScanJob(missingDir);
        ScanJob scanJob = libraryScanService.run(scanJobId);

        assertEquals("completed", scanJob.status);
        assertEquals(0, scanJob.totalFiles);
        assertEquals(0, scanJob.scannedFiles);
        assertEquals(0, scanJob.newFiles);
        assertEquals(1, scanJob.skippedFiles);
        assertEquals(0, scanJob.errorFiles);
        assertNotNull(scanJob.errorMessage);
    }

    @Transactional
    Long createScanJob(Path path) {
        ScanJob scanJob = new ScanJob();
        scanJob.jobType = "library_scan";
        scanJob.status = "pending";
        scanJob.musicDirs = path.toString();
        scanJob.persist();
        return scanJob.id;
    }
}
