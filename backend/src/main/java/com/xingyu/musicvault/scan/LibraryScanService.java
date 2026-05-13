package com.xingyu.musicvault.scan;

import com.xingyu.musicvault.common.ConflictException;
import com.xingyu.musicvault.config.MusicVaultConfig;
import com.xingyu.musicvault.job.ScanJob;
import com.xingyu.musicvault.library.TrackFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
            throw new ConflictException("Scan job is already running");
        }
        if ("completed".equals(scanJob.status)) {
            throw new ConflictException("Completed scan job cannot be run again");
        }
        if (!"pending".equals(scanJob.status) && !"failed".equals(scanJob.status)) {
            throw new BadRequestException("Scan job status must be pending or failed to run");
        }

        prepare(scanJob);
        List<String> messages = new ArrayList<>();

        try {
            List<Path> roots = resolveValidatedMusicDirs(scanJob);

            for (Path root : roots) {
                scanRoot(scanJob, root, messages);
            }

            scanJob.status = "completed";
            scanJob.errorMessage = messages.isEmpty() ? null : String.join("; ", messages);
        } catch (Exception exception) {
            scanJob.status = "failed";
            scanJob.errorMessage = exception.getMessage() != null
                    ? exception.getMessage()
                    : exception.getClass().getSimpleName();
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
        if (requestedDirs.isEmpty()) {
            throw new IllegalArgumentException("No music directories configured");
        }

        List<Path> allowedRoots = config.musicDirs().stream()
                .map(this::toValidatedAllowedRoot)
                .toList();
        if (allowedRoots.isEmpty()) {
            throw new IllegalArgumentException("No allowed music directories configured");
        }

        List<Path> roots = new ArrayList<>();
        for (String dir : requestedDirs) {
            Path requestedPath = Path.of(dir);
            rejectPathTraversal(requestedPath);
            Path realPath = toReadableDirectory(requestedPath, "Music directory");
            rejectUnsafeRoot(realPath);
            if (allowedRoots.stream().noneMatch(realPath::startsWith)) {
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
            throw new IllegalArgumentException(label + " does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException(label + " is not a directory: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new IllegalArgumentException(label + " is not readable: " + path);
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException(label + " cannot be resolved: " + path);
        }
    }

    private void rejectPathTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                throw new IllegalArgumentException("Music directory must not contain path traversal: " + path);
            }
        }
    }

    private void rejectUnsafeRoot(Path path) {
        if (UNSAFE_ROOTS.contains(path)) {
            throw new IllegalArgumentException("Music directory is not an allowed scan root: " + path);
        }
    }

    private void scanRoot(ScanJob scanJob, Path root, List<String> messages) {
        if (!Files.exists(root)) {
            scanJob.skippedFiles++;
            messages.add("Music directory does not exist: " + root);
            return;
        }
        if (!Files.isDirectory(root)) {
            scanJob.skippedFiles++;
            messages.add("Music path is not a directory: " + root);
            return;
        }
        if (!Files.isReadable(root)) {
            scanJob.skippedFiles++;
            messages.add("Music directory is not readable: " + root);
            return;
        }

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> scanFile(scanJob, path, messages));
        } catch (IOException | UncheckedIOException exception) {
            scanJob.errorFiles++;
            messages.add("Failed to scan directory " + root + ": " + exception.getMessage());
        }
    }

    private void scanFile(ScanJob scanJob, Path path, List<String> messages) {
        scanJob.totalFiles++;

        String fileName = path.getFileName().toString();
        String ext = extensionOf(fileName);
        if (!AUDIO_EXTENSIONS.contains(ext)) {
            scanJob.skippedFiles++;
            return;
        }
        if (!Files.isReadable(path)) {
            scanJob.errorFiles++;
            messages.add("Audio file is not readable: " + path);
            return;
        }

        try {
            upsertTrackFile(scanJob, path, fileName, ext);
            scanJob.scannedFiles++;
        } catch (IOException exception) {
            scanJob.errorFiles++;
            messages.add("Failed to read audio file " + path + ": " + exception.getMessage());
        }
    }

    private void upsertTrackFile(ScanJob scanJob, Path path, String fileName, String ext) throws IOException {
        String filePath = path.toAbsolutePath().normalize().toString();
        TrackFile trackFile = TrackFile.find("filePath", filePath).firstResult();
        boolean isNew = trackFile == null;
        if (isNew) {
            trackFile = new TrackFile();
            trackFile.filePath = filePath;
        }

        trackFile.fileName = fileName;
        trackFile.fileExt = ext;
        trackFile.fileSize = Files.size(path);
        trackFile.lastModifiedAt = toLocalDateTime(Files.getLastModifiedTime(path).toInstant());
        trackFile.scanJobId = scanJob.id;

        if (isNew) {
            trackFile.persist();
            scanJob.newFiles++;
        } else {
            scanJob.updatedFiles++;
        }
    }

    private String extensionOf(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
