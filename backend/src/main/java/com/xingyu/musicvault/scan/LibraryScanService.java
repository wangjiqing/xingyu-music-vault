package com.xingyu.musicvault.scan;

import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

@ApplicationScoped
public class LibraryScanService {
    private static final Logger LOG = Logger.getLogger(LibraryScanService.class);
    private static final Set<String> AUDIO_EXTENSIONS = Set.of("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus");
    private static final Set<Path> UNSAFE_ROOTS = Set.of(
            Paths.get("/"),
            Paths.get("/etc"),
            Paths.get("/Users"),
            Paths.get("/home")
    );

    @Inject
    MusicVaultConfig config;

    @Transactional
    public ScanJob run(Long scanJobId) {
        ScanJob scanJob = ScanJob.findById(scanJobId);
        if (scanJob == null) {
            throw new NotFoundException("Scan job not found");
        }
        if ("running".equals(scanJob.status)) {
            LOG.infof("Reject scan job run because it is already running: id=%d", scanJob.id);
            throw new ConflictException("Scan job is already running");
        }
        if ("completed".equals(scanJob.status)) {
            LOG.infof("Reject scan job run because it is already completed: id=%d", scanJob.id);
            throw new ConflictException("Completed scan job cannot be run again");
        }
        if (!"pending".equals(scanJob.status) && !"failed".equals(scanJob.status)) {
            LOG.warnf("Reject scan job run because status is invalid: id=%d status=%s", scanJob.id, scanJob.status);
            throw new BadRequestException("Scan job status must be pending or failed to run");
        }

        LOG.infof("Starting library scan job: id=%d status=%s musicDirs=%s", scanJob.id, scanJob.status, scanJob.musicDirs);
        prepare(scanJob);
        List<String> messages = new ArrayList<>();

        try {
            List<Path> roots = resolveValidatedMusicDirs(scanJob);
            LOG.infof("Library scan job resolved scan roots: id=%d roots=%s", scanJob.id, roots);

            for (Path root : roots) {
                scanRoot(scanJob, root, messages);
            }

            scanJob.status = "completed";
            scanJob.errorMessage = messages.isEmpty() ? null : String.join("; ", messages);
            LOG.infof(
                    "Completed library scan job: id=%d total=%d scanned=%d new=%d updated=%d skipped=%d errors=%d",
                    scanJob.id,
                    scanJob.totalFiles,
                    scanJob.scannedFiles,
                    scanJob.newFiles,
                    scanJob.updatedFiles,
                    scanJob.skippedFiles,
                    scanJob.errorFiles
            );
        } catch (Exception exception) {
            scanJob.status = "failed";
            scanJob.errorMessage = exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName();
            LOG.errorf(
                    exception,
                    "Failed library scan job: id=%d status=%s message=%s",
                    scanJob.id,
                    scanJob.status,
                    scanJob.errorMessage
            );
        } finally {
            scanJob.finishedAt = LocalDateTime.now();
        }

        return scanJob;
    }

    private void prepare(ScanJob scanJob) {
        scanJob.status = "running";
        scanJob.totalFiles = 0;
        scanJob.scannedFiles = 0;
        scanJob.newFiles = 0;
        scanJob.updatedFiles = 0;
        scanJob.skippedFiles = 0;
        scanJob.errorFiles = 0;
        scanJob.errorMessage = null;
        scanJob.startedAt = LocalDateTime.now();
        scanJob.finishedAt = null;
    }

    private List<String> resolveMusicDirs(ScanJob scanJob) {
        if (scanJob.musicDirs != null && !scanJob.musicDirs.isBlank()) {
            return List.of(scanJob.musicDirs.split(",")).stream()
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .toList();
        }
        return config.musicDirs();
    }

    private List<Path> resolveValidatedMusicDirs(ScanJob scanJob) {
        List<String> requestedDirs = resolveMusicDirs(scanJob);
        LOG.debugf("Library scan requested directories: id=%d dirs=%s", scanJob.id, requestedDirs);
        if (requestedDirs.isEmpty()) {
            LOG.warnf("Path validation failed because no music directories were configured: id=%d", scanJob.id);
            throw new IllegalArgumentException("No music directories configured");
        }

        List<Path> allowedRoots = config.musicDirs().stream()
                .map(this::toValidatedAllowedRoot)
                .toList();
        LOG.infof("Allowed music roots for scan job %d: %s", scanJob.id, allowedRoots);
        if (allowedRoots.isEmpty()) {
            LOG.warnf("Path validation failed because no allowed music roots were configured: id=%d", scanJob.id);
            throw new IllegalArgumentException("No allowed music directories configured");
        }

        List<Path> roots = new ArrayList<>();
        for (String dir : requestedDirs) {
            Path requestedPath = Path.of(dir);
            rejectPathTraversal(requestedPath);
            Path realPath = toReadableDirectory(requestedPath, "Music directory");
            rejectUnsafeRoot(realPath);
            if (allowedRoots.stream().noneMatch(realPath::startsWith)) {
                LOG.warnf(
                        "Path validation failed because requested directory is outside allowed roots: id=%d requested=%s realPath=%s allowedRoots=%s",
                        scanJob.id,
                        requestedPath,
                        realPath,
                        allowedRoots
                );
                throw new IllegalArgumentException("Music directory is outside allowed roots: " + requestedPath);
            }
            roots.add(realPath);
        }
        return roots;
    }

    private Path toValidatedAllowedRoot(String dir) {
        Path path = Path.of(dir);
        rejectPathTraversal(path);
        Path realPath = toReadableDirectory(path, "Allowed music directory");
        rejectUnsafeRoot(realPath);
        return realPath;
    }

    private Path toReadableDirectory(Path path, String label) {
        if (!Files.exists(path)) {
            LOG.warnf("%s does not exist: %s", label, path);
            throw new IllegalArgumentException(label + " does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            LOG.warnf("%s is not a directory: %s", label, path);
            throw new IllegalArgumentException(label + " is not a directory: " + path);
        }
        if (!Files.isReadable(path)) {
            LOG.warnf("%s is not readable: %s", label, path);
            throw new IllegalArgumentException(label + " is not readable: " + path);
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            LOG.warnf(exception, "%s cannot be resolved: %s", label, path);
            throw new IllegalArgumentException(label + " cannot be resolved: " + path);
        }
    }

    private void rejectPathTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                LOG.warnf("Path validation failed because path contains traversal: %s", path);
                throw new IllegalArgumentException("Music directory must not contain path traversal: " + path);
            }
        }
    }

    private void rejectUnsafeRoot(Path path) {
        if (UNSAFE_ROOTS.contains(path)) {
            LOG.warnf("Path validation failed because path is an unsafe root: %s", path);
            throw new IllegalArgumentException("Music directory is not an allowed scan root: " + path);
        }
    }

    private void scanRoot(ScanJob scanJob, Path root, List<String> messages) {
        LOG.infof("Scanning music directory: jobId=%d root=%s", scanJob.id, root);
        if (!Files.exists(root)) {
            scanJob.skippedFiles++;
            messages.add("Music directory does not exist: " + root);
            LOG.warnf("Music directory does not exist during scan: jobId=%d root=%s", scanJob.id, root);
            return;
        }
        if (!Files.isDirectory(root)) {
            scanJob.skippedFiles++;
            messages.add("Music path is not a directory: " + root);
            LOG.warnf("Music path is not a directory during scan: jobId=%d root=%s", scanJob.id, root);
            return;
        }
        if (!Files.isReadable(root)) {
            scanJob.skippedFiles++;
            messages.add("Music directory is not readable: " + root);
            LOG.warnf("Music directory is not readable during scan: jobId=%d root=%s", scanJob.id, root);
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> scanFile(scanJob, path, messages));
        } catch (IOException | UncheckedIOException exception) {
            scanJob.errorFiles++;
            messages.add("Failed to scan directory " + root + ": " + exception.getMessage());
            LOG.errorf(exception, "Failed to walk music directory: jobId=%d root=%s", scanJob.id, root);
        }
    }

    private void scanFile(ScanJob scanJob, Path path, List<String> messages) {
        scanJob.totalFiles++;

        if (isHidden(path)) {
            scanJob.skippedFiles++;
            LOG.debugf("Skipping hidden file: jobId=%d path=%s", scanJob.id, path);
            return;
        }

        String fileName = path.getFileName().toString();
        String ext = extensionOf(fileName);
        if (!AUDIO_EXTENSIONS.contains(ext)) {
            scanJob.skippedFiles++;
            LOG.debugf("Skipping non-audio file: jobId=%d path=%s", scanJob.id, path);
            return;
        }
        if (!Files.isReadable(path)) {
            scanJob.errorFiles++;
            messages.add("Audio file is not readable: " + path);
            LOG.warnf("Audio file is not readable: jobId=%d path=%s", scanJob.id, path);
            return;
        }

        try {
            UpsertResult result = upsertTrackFile(scanJob, path, fileName, ext);
            if (result == UpsertResult.INSERTED) {
                scanJob.newFiles++;
            } else if (result == UpsertResult.UPDATED) {
                scanJob.updatedFiles++;
            } else {
                scanJob.skippedFiles++;
            }
            scanJob.scannedFiles++;
        } catch (IOException exception) {
            scanJob.errorFiles++;
            messages.add("Failed to read audio file " + path + ": " + exception.getMessage());
            LOG.errorf(exception, "Failed to read audio file: jobId=%d path=%s", scanJob.id, path);
        }
    }

    private UpsertResult upsertTrackFile(ScanJob scanJob, Path path, String fileName, String ext) throws IOException {
        String filePath = path.toAbsolutePath().normalize().toString();
        TrackFile trackFile = TrackFile.find("filePath", filePath).firstResult();
        long fileSize = Files.size(path);
        LocalDateTime lastModifiedAt = toLocalDateTime(Files.getLastModifiedTime(path).toInstant());
        boolean isNew = trackFile == null;
        if (isNew) {
            trackFile = new TrackFile();
            trackFile.filePath = filePath;
        } else if (trackFile.trackId != null && trackFile.fileSize == fileSize && sameTime(trackFile.lastModifiedAt, lastModifiedAt)) {
            LOG.debugf("Skipping unchanged track file: jobId=%d path=%s", scanJob.id, filePath);
            return UpsertResult.SKIPPED;
        }

        trackFile.fileName = fileName;
        trackFile.fileExt = ext;
        trackFile.fileSize = fileSize;
        trackFile.lastModifiedAt = lastModifiedAt;
        trackFile.scanJobId = scanJob.id;
        trackFile.trackId = upsertTrack(trackFile.trackId, fileName).id;

        if (isNew) {
            trackFile.persist();
            LOG.debugf("Inserted track file: jobId=%d path=%s ext=%s size=%d", scanJob.id, filePath, ext, trackFile.fileSize);
            return UpsertResult.INSERTED;
        } else {
            LOG.debugf("Updated track file: jobId=%d path=%s ext=%s size=%d", scanJob.id, filePath, ext, trackFile.fileSize);
            return UpsertResult.UPDATED;
        }
    }

    private Track upsertTrack(Long trackId, String fileName) {
        ParsedMetadata metadata = parseMetadata(fileName);
        Track track = trackId == null ? null : Track.findById(trackId);
        if (track == null) {
            track = new Track();
        }

        track.title = metadata.title();
        track.normalizedTitle = normalizeTitle(metadata.title());
        track.artist = metadata.artist();
        track.album = metadata.album();
        track.albumArtist = metadata.albumArtist();
        track.duration = metadata.duration();

        if (!track.isPersistent()) {
            track.persist();
        }
        return track;
    }

    private ParsedMetadata parseMetadata(String fileName) {
        String baseName = stripExtension(fileName).trim();
        if (baseName.isEmpty()) {
            return new ParsedMetadata("Untitled", "Unknown", null, null, null);
        }

        String[] parts = baseName.split("\\s+-\\s+", 2);
        if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
            String artist = parts[0].trim();
            return new ParsedMetadata(parts[1].trim(), artist, null, artist, null);
        }
        return new ParsedMetadata(baseName, "Unknown", null, null, null);
    }

    private String stripExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private String normalizeTitle(String title) {
        return title.trim().toLowerCase(Locale.ROOT);
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isHidden(Path path) {
        for (Path segment : path) {
            if (segment.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private boolean sameTime(LocalDateTime left, LocalDateTime right) {
        if (left == null || right == null) {
            return false;
        }
        return Math.abs(Duration.between(left, right).toMillis()) < 1000;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    // TODO: v0.4+ can mark database rows missing when source files are deleted from disk.
    private enum UpsertResult {
        INSERTED,
        UPDATED,
        SKIPPED
    }

    private record ParsedMetadata(
            String title,
            String artist,
            String album,
            String albumArtist,
            Long duration
    ) {
    }
}
