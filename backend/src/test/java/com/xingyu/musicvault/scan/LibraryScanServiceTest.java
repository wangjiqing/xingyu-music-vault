package com.xingyu.musicvault.scan;

import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
class LibraryScanServiceTest {
    private static final Path ALLOWED_MUSIC_ROOT = Path.of("target/test-music");

    @Inject
    LibraryScanService libraryScanService;

    Path musicDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ALLOWED_MUSIC_ROOT);
        musicDir = Files.createTempDirectory(ALLOWED_MUSIC_ROOT, "service-test-");
        TrackFile.deleteAll();
        Track.deleteAll();
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
        assertEquals(2, Track.count());
    }

    @Test
    void scanSkipsMusicVaultTrashDirectory() throws IOException {
        Files.writeString(musicDir.resolve("active.flac"), "active");
        Path trashDir = musicDir.resolve(".music-vault-trash").resolve("1");
        Files.createDirectories(trashDir);
        Files.writeString(trashDir.resolve("trashed.flac"), "trashed");

        Long scanJobId = createScanJob(musicDir);
        ScanJob scanJob = libraryScanService.run(scanJobId);

        assertEquals("completed", scanJob.status);
        assertEquals(1, scanJob.totalFiles);
        assertEquals(1, scanJob.scannedFiles);
        assertEquals(1, scanJob.newFiles);
        assertEquals(0, scanJob.skippedFiles);
        assertEquals(1, TrackFile.count());
        assertNotNull(TrackFile.find("fileName", "active.flac").firstResult());
        assertNull(TrackFile.find("fileName", "trashed.flac").firstResult());
    }

    @Test
    void repeatedScanSkipsUnchangedTrackFiles() throws IOException {
        Path file = musicDir.resolve("a.flac");
        Files.writeString(file, "first");

        Long firstJobId = createScanJob(musicDir);
        libraryScanService.run(firstJobId);

        Long secondJobId = createScanJob(musicDir);
        ScanJob secondJob = libraryScanService.run(secondJobId);

        assertEquals("completed", secondJob.status);
        assertEquals(1, secondJob.totalFiles);
        assertEquals(1, secondJob.scannedFiles);
        assertEquals(0, secondJob.newFiles);
        assertEquals(0, secondJob.updatedFiles);
        assertEquals(1, secondJob.skippedFiles);
        assertEquals(1, TrackFile.count());

        TrackFile trackFile = TrackFile.find("fileName", "a.flac").firstResult();
        assertNotNull(trackFile);
        assertEquals(firstJobId, trackFile.scanJobId);
    }

    @Test
    @Transactional
    void repeatedScanRestoresTrashedTrackFileWhenOriginalFileExistsAgain() throws IOException {
        Path file = musicDir.resolve("restored.flac");
        Files.writeString(file, "first");

        Long firstJobId = createScanJob(musicDir);
        libraryScanService.run(firstJobId);

        TrackFile trashed = TrackFile.find("fileName", "restored.flac").firstResult();
        trashed.deleteStatus = "trashed";
        trashed.deletedAt = LocalDateTime.now();
        trashed.trashPath = musicDir.resolve(".music-vault-trash")
                .resolve(trashed.id.toString())
                .resolve("restored.flac")
                .toAbsolutePath()
                .normalize()
                .toString();

        Long secondJobId = createScanJob(musicDir);
        ScanJob secondJob = libraryScanService.run(secondJobId);

        assertEquals("completed", secondJob.status);
        assertEquals(1, secondJob.totalFiles);
        assertEquals(1, secondJob.scannedFiles);
        assertEquals(0, secondJob.newFiles);
        assertEquals(1, secondJob.updatedFiles);
        assertEquals(0, secondJob.skippedFiles);

        TrackFile restored = TrackFile.find("fileName", "restored.flac").firstResult();
        assertEquals("active", restored.deleteStatus);
        assertNull(restored.deletedAt);
        assertNull(restored.trashPath);
        assertEquals(secondJobId, restored.scanJobId);
    }

    @Test
    @Transactional
    void repeatedScanRepairsLegacyTrackFileWithoutTrackId() throws IOException {
        Path file = musicDir.resolve("周杰伦 - 晴天.flac");
        Files.writeString(file, "music");

        TrackFile trackFile = new TrackFile();
        trackFile.filePath = file.toAbsolutePath().normalize().toString();
        trackFile.fileName = file.getFileName().toString();
        trackFile.fileExt = "flac";
        trackFile.fileSize = Files.size(file);
        trackFile.lastModifiedAt = LocalDateTime.ofInstant(Files.getLastModifiedTime(file).toInstant(), java.time.ZoneId.systemDefault());
        trackFile.persist();

        Long scanJobId = createScanJob(musicDir);
        ScanJob scanJob = libraryScanService.run(scanJobId);

        assertEquals("completed", scanJob.status);
        assertEquals(0, scanJob.newFiles);
        assertEquals(1, scanJob.updatedFiles);

        TrackFile repaired = TrackFile.find("fileName", "周杰伦 - 晴天.flac").firstResult();
        assertNotNull(repaired.trackId);
        Track track = Track.findById(repaired.trackId);
        assertNotNull(track);
        assertEquals("晴天", track.title);
        assertEquals("周杰伦", track.artist);
    }

    @Test
    void repeatedScanUpdatesChangedTrackFiles() throws IOException, InterruptedException {
        Path file = musicDir.resolve("a.flac");
        Files.writeString(file, "first");

        Long firstJobId = createScanJob(musicDir);
        libraryScanService.run(firstJobId);

        Thread.sleep(5);
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
    void parsesFallbackMetadataFromFileName() throws IOException {
        Files.writeString(musicDir.resolve("周杰伦 - 晴天.flac"), "music");

        Long scanJobId = createScanJob(musicDir);
        libraryScanService.run(scanJobId);

        TrackFile trackFile = TrackFile.find("fileName", "周杰伦 - 晴天.flac").firstResult();
        assertNotNull(trackFile);

        Track track = Track.findById(trackFile.trackId);
        assertNotNull(track);
        assertEquals("晴天", track.title);
        assertEquals("周杰伦", track.artist);
        assertNull(track.albumArtist);
        assertNull(track.album);
        assertNull(track.duration);
    }

    @Test
    void missingDirectoryFailsWithClearMessage() {
        Path missingDir = musicDir.resolve("missing");

        Long scanJobId = createScanJob(missingDir);
        ScanJob scanJob = libraryScanService.run(scanJobId);

        assertEquals("failed", scanJob.status);
        assertEquals(0, scanJob.totalFiles);
        assertEquals(0, scanJob.scannedFiles);
        assertEquals(0, scanJob.newFiles);
        assertEquals(0, scanJob.skippedFiles);
        assertEquals(0, scanJob.errorFiles);
        assertNotNull(scanJob.errorMessage);
    }

    @Test
    void failedScanJobCanRunAgain() throws IOException {
        Path missingDir = musicDir.resolve("missing");
        Long scanJobId = createScanJob(missingDir);
        ScanJob failedJob = libraryScanService.run(scanJobId);
        assertEquals("failed", failedJob.status);

        Files.createDirectories(missingDir);
        Files.writeString(missingDir.resolve("retry.mp3"), "retry");

        ScanJob rerunJob = libraryScanService.run(scanJobId);

        assertEquals("completed", rerunJob.status);
        assertEquals(1, rerunJob.totalFiles);
        assertEquals(1, rerunJob.scannedFiles);
        assertEquals(1, rerunJob.newFiles);
        assertNull(rerunJob.errorMessage);
    }

    @Test
    void pathTraversalIsRejected() {
        Path traversal = musicDir.resolve("..").resolve("outside");

        Long scanJobId = createScanJob(traversal);
        ScanJob scanJob = libraryScanService.run(scanJobId);

        assertEquals("failed", scanJob.status);
        assertNotNull(scanJob.errorMessage);
    }

    @Test
    void directoryOutsideAllowedRootsIsRejected() throws IOException {
        Path outsideDir = Files.createTempDirectory("music-vault-outside-test");

        Long scanJobId = createScanJob(outsideDir);
        ScanJob scanJob = libraryScanService.run(scanJobId);

        assertEquals("failed", scanJob.status);
        assertNotNull(scanJob.errorMessage);
    }

    @Test
    void completedScanJobCannotRunAgain() {
        Long scanJobId = createScanJob(musicDir);
        libraryScanService.run(scanJobId);

        assertThrows(ConflictException.class, () -> libraryScanService.run(scanJobId));
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
