package com.xingyu.musicvault.lyrics;

import com.xingyu.musicvault.config.MusicVaultConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class ManagedLyricAssetPathService {
    private static final Logger LOG = Logger.getLogger(ManagedLyricAssetPathService.class);
    private static final String LRC_FILE_NAME = "lyrics.lrc";
    private static final String SWLRC_FILE_NAME = "lyrics.swlrc";

    @Inject
    MusicVaultConfig config;

    public AlignmentAssetPaths alignmentAssetPaths(Long songId, String jobId) {
        if (songId == null) {
            throw new BadRequestException("Music id is required for alignment lyric asset path");
        }
        if (jobId == null || jobId.isBlank()) {
            throw new BadRequestException("Alignment job id is required for lyric asset path");
        }
        Path managedRoot = managedAlignmentRootForImport();
        Path songDir = managedRoot.resolve(String.valueOf(songId)).normalize();
        Path finalDir = songDir.resolve(jobId.trim()).normalize();
        if (!finalDir.startsWith(managedRoot) || finalDir.equals(managedRoot)) {
            throw new BadRequestException("Alignment lyric asset path is invalid");
        }
        return new AlignmentAssetPaths(
                managedRoot,
                finalDir,
                finalDir.resolve(LRC_FILE_NAME).normalize(),
                finalDir.resolve(SWLRC_FILE_NAME).normalize()
        );
    }

    public boolean isManagedAlignmentPath(Path path) {
        if (path == null || !alignmentLyricsRootConfigured()) {
            return false;
        }
        if (!alignmentLyricsSubdirConfigured()) {
            LOG.warn("Managed alignment lyric subdir is blank; scan exclusion is disabled until configuration is fixed");
            return false;
        }
        Path managedRoot;
        try {
            managedRoot = existingManagedAlignmentRoot();
        } catch (BadRequestException exception) {
            LOG.warnf(exception, "Managed alignment lyric root cannot be resolved; scan exclusion is disabled");
            return false;
        }
        Path candidate = realPath(path);
        if (candidate == null) {
            LOG.warnf("Managed alignment lyric path cannot be resolved; scan exclusion skipped for path=%s", path);
            return false;
        }
        return candidate.startsWith(managedRoot);
    }

    public Path managedAlignmentRoot() {
        return existingManagedAlignmentRoot();
    }

    private Path managedAlignmentRootForImport() {
        Path lyricsRoot = alignmentLyricsRoot();
        Path subdir = alignmentLyricsSubdir();
        Path managedRoot = lyricsRoot.resolve(subdir).normalize();
        if (!managedRoot.startsWith(lyricsRoot) || managedRoot.equals(lyricsRoot)) {
            throw new BadRequestException("Managed alignment lyric directory is invalid");
        }
        try {
            Files.createDirectories(managedRoot);
        } catch (IOException exception) {
            throw new BadRequestException("Managed alignment lyric root cannot be created: " + managedRoot, exception);
        }
        return realDirectory(managedRoot, "Managed alignment lyric root");
    }

    private Path existingManagedAlignmentRoot() {
        Path lyricsRoot = alignmentLyricsRoot();
        Path subdir = alignmentLyricsSubdir();
        Path managedRoot = lyricsRoot.resolve(subdir).normalize();
        if (!managedRoot.startsWith(lyricsRoot) || managedRoot.equals(lyricsRoot)) {
            throw new BadRequestException("Managed alignment lyric directory is invalid");
        }
        return realDirectory(managedRoot, "Managed alignment lyric root");
    }

    public Path alignmentLyricsRoot() {
        String configured = config.alignmentLyricsRoot().orElse(null);
        if (configured == null || configured.isBlank()) {
            throw new BadRequestException("music-vault.alignment-lyrics-root must be configured for alignment lyric import");
        }
        Path root = realDirectory(Path.of(configured.trim()), "Alignment lyric root");
        List<Path> lyricRoots = config.lyricDirs().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> realDirectory(Path.of(value.trim()), "Lyric directory"))
                .toList();
        if (lyricRoots.stream().noneMatch(lyricRoot -> Objects.equals(lyricRoot, root))) {
            throw new BadRequestException("music-vault.alignment-lyrics-root must match one configured lyric directory");
        }
        return root;
    }

    private boolean alignmentLyricsRootConfigured() {
        String configured = config.alignmentLyricsRoot().orElse(null);
        return configured != null && !configured.isBlank();
    }

    private boolean alignmentLyricsSubdirConfigured() {
        String configured = config.alignmentLyricsSubdir();
        return configured != null && !configured.isBlank();
    }

    private Path alignmentLyricsSubdir() {
        String configured = config.alignmentLyricsSubdir();
        if (configured == null || configured.isBlank()) {
            throw new BadRequestException("music-vault.alignment-lyrics-subdir must not be blank");
        }
        Path subdir = Path.of(configured.trim());
        if (subdir.isAbsolute()) {
            throw new BadRequestException("music-vault.alignment-lyrics-subdir must be relative");
        }
        for (Path segment : subdir) {
            if ("..".equals(segment.toString())) {
                throw new BadRequestException("music-vault.alignment-lyrics-subdir must not contain path traversal");
            }
        }
        return subdir.normalize();
    }

    private Path realDirectory(Path path, String label) {
        try {
            if (!Files.exists(path)) {
                throw new BadRequestException(label + " does not exist: " + path);
            }
            if (!Files.isDirectory(path)) {
                throw new BadRequestException(label + " is not a directory: " + path);
            }
            return path.toRealPath();
        } catch (IOException exception) {
            throw new BadRequestException(label + " cannot be resolved: " + path, exception);
        }
    }

    private Path realPath(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return null;
        }
    }

    public record AlignmentAssetPaths(
            Path managedRoot,
            Path finalDir,
            Path lrcPath,
            Path swlrcPath
    ) {
    }
}
