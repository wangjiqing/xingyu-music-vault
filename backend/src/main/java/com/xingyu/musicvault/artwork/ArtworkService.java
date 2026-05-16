package com.xingyu.musicvault.artwork;

import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkScanResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.MusicArtworkResponse;
import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class ArtworkService {
    public static final String TRACK_COVER = "track_cover";

    private static final Logger LOG = Logger.getLogger(ArtworkService.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");

    @ConfigProperty(name = "app.artwork.scan-dir")
    String defaultScanDir;

    @Inject
    ArtworkRepository artworkRepository;

    @Inject
    MusicArtworkBindingRepository bindingRepository;

    @Transactional
    public ArtworkScanResponse scan(String requestedPath) {
        Path configuredRoot = configuredArtworkRoot();
        Path root = resolveScanRoot(requestedPath, configuredRoot);
        ScanCounters counters = new ScanCounters();
        Map<String, List<TrackFile>> trackFilesByBaseName = trackFilesByBaseName();
        LOG.infof("Scanning artwork directory: root=%s", root);

        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(this::isSupportedImage)
                    .forEach(path -> scanFile(path, configuredRoot, trackFilesByBaseName, counters));
        } catch (IOException | UncheckedIOException exception) {
            throw new BadRequestException("Failed to scan artwork directory: " + exception.getMessage(), exception);
        }

        return new ArtworkScanResponse(
                root.toString(),
                counters.totalFiles,
                counters.imported,
                counters.duplicateFiles,
                counters.autoBound,
                counters.unmatched,
                counters.failed
        );
    }

    public PageResponse<ArtworkResponse> list(Integer page, Integer size) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);
        PanacheQuery<Artwork> query = Artwork.findAll(Sort.descending("createdAt"));
        long total = query.count();
        List<ArtworkResponse> items = query.page(Page.of(pageValue, sizeValue)).list().stream()
                .map(ArtworkResponse::from)
                .toList();
        return new PageResponse<>(items, pageValue, sizeValue, total);
    }

    public ArtworkResponse get(Long id) {
        return ArtworkResponse.from(findArtwork(id));
    }

    public Response file(Long id) {
        Artwork artwork = findArtwork(id);
        Path path = resolveArtworkFile(artwork);
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new NotFoundException("Artwork file not found");
        }
        return Response.ok(path.toFile(), artwork.mimeType)
                .header("Content-Disposition", "inline; filename=\"" + artwork.fileName.replace("\"", "") + "\"")
                .build();
    }

    @Transactional
    public MusicArtworkResponse bind(Long musicId, Long artworkId) {
        if (artworkId == null) {
            throw new BadRequestException("artworkId is required");
        }
        TrackFile trackFile = TrackFile.findById(musicId);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        Artwork artwork = findArtwork(artworkId);

        MusicArtworkBinding primary = bindingRepository.findPrimaryTrackCoverByMusicId(musicId);
        if (primary != null && Objects.equals(primary.artworkId, artworkId)) {
            updateTrackArtworkStatus(trackFile, "matched");
            return toMusicArtworkResponse(musicId, artwork);
        }
        if (primary != null) {
            primary.isPrimary = false;
        }

        MusicArtworkBinding binding = new MusicArtworkBinding();
        binding.musicId = musicId;
        binding.artworkId = artworkId;
        binding.relationType = TRACK_COVER;
        binding.isPrimary = true;
        binding.persist();
        updateTrackArtworkStatus(trackFile, "matched");
        return toMusicArtworkResponse(musicId, artwork);
    }

    @Transactional
    public MusicArtworkResponse unbind(Long musicId) {
        TrackFile trackFile = TrackFile.findById(musicId);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        MusicArtworkBinding primary = bindingRepository.findPrimaryTrackCoverByMusicId(musicId);
        if (primary != null) {
            primary.delete();
        }
        updateTrackArtworkStatus(trackFile, "missing");
        return MusicArtworkResponse.missing(musicId);
    }

    public Map<Long, PrimaryArtworkSummary> primaryArtworkForMusicIds(List<Long> musicIds) {
        if (musicIds == null || musicIds.isEmpty()) {
            return Map.of();
        }

        List<MusicArtworkBinding> bindings = bindingRepository.findPrimaryTrackCoversByMusicIds(musicIds);
        if (bindings.isEmpty()) {
            return Map.of();
        }

        Map<Long, Artwork> artworksById = Artwork.<Artwork>list(
                "id in ?1",
                bindings.stream().map(binding -> binding.artworkId).distinct().toList()
        ).stream().collect(Collectors.toMap(artwork -> artwork.id, Function.identity()));

        Map<Long, PrimaryArtworkSummary> result = new HashMap<>();
        for (MusicArtworkBinding binding : bindings) {
            Artwork artwork = artworksById.get(binding.artworkId);
            if (artwork == null) {
                continue;
            }
            result.put(binding.musicId, new PrimaryArtworkSummary(
                    artwork.id,
                    "/api/artworks/" + artwork.id + "/file",
                    artwork.fileName
            ));
        }
        return result;
    }

    private void scanFile(
            Path path,
            Path configuredRoot,
            Map<String, List<TrackFile>> trackFilesByBaseName,
            ScanCounters counters
    ) {
        counters.totalFiles++;
        try {
            Path realPath = path.toRealPath();
            if (!realPath.startsWith(configuredRoot)) {
                counters.failed++;
                LOG.warnf("Artwork file outside scan root: path=%s realPath=%s", path, realPath);
                return;
            }
            String matchBaseName = baseNameOf(realPath.getFileName().toString());
            String filePath = realPath.toString();
            Artwork artwork = artworkRepository.findByFilePath(filePath);
            if (artwork != null) {
                counters.duplicateFiles++;
                autoBind(artwork, matchBaseName, trackFilesByBaseName, counters);
                return;
            }

            String hash = sha256(realPath);
            artwork = artworkRepository.findByHash(hash);
            if (artwork != null) {
                counters.duplicateFiles++;
                autoBind(artwork, matchBaseName, trackFilesByBaseName, counters);
                return;
            }

            ImageSize imageSize = readImageSize(realPath);
            artwork = new Artwork();
            artwork.filePath = filePath;
            artwork.fileName = realPath.getFileName().toString();
            artwork.fileExt = extensionOf(realPath);
            artwork.mimeType = mimeTypeOf(artwork.fileExt);
            artwork.fileSize = Files.size(realPath);
            artwork.width = imageSize.width();
            artwork.height = imageSize.height();
            artwork.hash = hash;
            artwork.sourceType = "local";
            artwork.sourcePath = filePath;
            artwork.title = titleOf(artwork.fileName);
            artwork.persist();
            counters.imported++;
            autoBind(artwork, matchBaseName, trackFilesByBaseName, counters);
        } catch (Exception exception) {
            counters.failed++;
            LOG.warnf(exception, "Failed to import artwork file: path=%s", path);
        }
    }

    private void autoBind(
            Artwork artwork,
            String matchBaseName,
            Map<String, List<TrackFile>> trackFilesByBaseName,
            ScanCounters counters
    ) {
        List<TrackFile> candidates = trackFilesByBaseName.getOrDefault(matchBaseName, List.of());
        if (candidates.isEmpty()) {
            counters.unmatched++;
            return;
        }

        for (TrackFile trackFile : candidates) {
            if (bindingRepository.findPrimaryTrackCoverByMusicId(trackFile.id) != null) {
                continue;
            }
            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = trackFile.id;
            binding.artworkId = artwork.id;
            binding.relationType = TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
            updateTrackArtworkStatus(trackFile, "matched");
            counters.autoBound++;
        }
    }

    private Map<String, List<TrackFile>> trackFilesByBaseName() {
        return TrackFile.<TrackFile>listAll().stream()
                .collect(Collectors.groupingBy(this::baseNameOf));
    }

    private String baseNameOf(TrackFile trackFile) {
        String fileName = trackFile.fileName;
        if ((fileName == null || fileName.isBlank()) && trackFile.filePath != null && !trackFile.filePath.isBlank()) {
            fileName = Path.of(trackFile.filePath).getFileName().toString();
        }
        return baseNameOf(fileName);
    }

    private Artwork findArtwork(Long id) {
        Artwork artwork = artworkRepository.findById(id);
        if (artwork == null) {
            throw new NotFoundException("Artwork not found");
        }
        return artwork;
    }

    private MusicArtworkResponse toMusicArtworkResponse(Long musicId, Artwork artwork) {
        return new MusicArtworkResponse(
                musicId,
                "BOUND",
                artwork.id,
                "/api/artworks/" + artwork.id + "/file",
                artwork.fileName
        );
    }

    private void updateTrackArtworkStatus(TrackFile trackFile, String artworkStatus) {
        if (trackFile.trackId == null) {
            return;
        }
        Track track = Track.findById(trackFile.trackId);
        if (track != null) {
            track.artworkStatus = artworkStatus;
        }
    }

    private Path resolveArtworkFile(Artwork artwork) {
        try {
            Path configuredRoot = configuredArtworkRoot();
            Path requested = Path.of(artwork.filePath);
            rejectPathTraversal(requested);
            Path realPath = requested.toRealPath();
            if (!realPath.startsWith(configuredRoot)) {
                LOG.warnf("Reject artwork file outside scan root: artworkId=%d path=%s realPath=%s",
                        artwork.id, artwork.filePath, realPath);
                throw new NotFoundException("Artwork file not found");
            }
            return realPath;
        } catch (IOException exception) {
            throw new NotFoundException("Artwork file not found", exception);
        }
    }

    private Path resolveScanRoot(String requestedPath, Path configuredRoot) {
        String rawPath = requestedPath;
        if (rawPath == null || rawPath.isBlank()) {
            rawPath = defaultScanDir;
        }
        if (rawPath == null || rawPath.isBlank()) {
            throw new BadRequestException("No artwork scan directory configured");
        }

        Path requested = Path.of(rawPath.trim());
        rejectPathTraversal(requested);
        Path realPath = toReadableDirectory(requested);
        if (!realPath.startsWith(configuredRoot)) {
            throw new BadRequestException("Artwork directory is outside configured scan root: " + requested);
        }
        return realPath;
    }

    private Path configuredArtworkRoot() {
        if (defaultScanDir == null || defaultScanDir.isBlank()) {
            throw new BadRequestException("No artwork scan directory configured");
        }
        return toReadableDirectory(Path.of(defaultScanDir));
    }

    private Path toReadableDirectory(Path path) {
        if (!Files.exists(path)) {
            throw new BadRequestException("Artwork directory does not exist: " + path);
        }
        if (!Files.isDirectory(path)) {
            throw new BadRequestException("Artwork path is not a directory: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new BadRequestException("Artwork directory is not readable: " + path);
        }
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new BadRequestException("Artwork directory cannot be resolved: " + path, exception);
        }
    }

    private void rejectPathTraversal(Path path) {
        for (Path segment : path) {
            if ("..".equals(segment.toString())) {
                throw new BadRequestException("Artwork directory must not contain path traversal: " + path);
            }
        }
    }

    private boolean isSupportedImage(Path path) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(path));
    }

    private ImageSize readImageSize(Path path) throws IOException {
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            if (hasSupportedImageSignature(path)) {
                LOG.warnf("Artwork image dimensions could not be read: path=%s", path);
                return new ImageSize(null, null);
            }
            throw new IOException("Artwork file is not a readable image: " + path);
        }
        return new ImageSize(image.getWidth(), image.getHeight());
    }

    private boolean hasSupportedImageSignature(Path path) throws IOException {
        byte[] header = new byte[12];
        int read;
        try (InputStream inputStream = Files.newInputStream(path)) {
            read = inputStream.read(header);
        }
        if (read < 4) {
            return false;
        }
        boolean jpeg = (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8;
        boolean png = read >= 8
                && (header[0] & 0xff) == 0x89
                && header[1] == 'P'
                && header[2] == 'N'
                && header[3] == 'G'
                && (header[4] & 0xff) == 0x0d
                && (header[5] & 0xff) == 0x0a
                && (header[6] & 0xff) == 0x1a
                && (header[7] & 0xff) == 0x0a;
        boolean webp = read >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
        return jpeg || png || webp;
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream inputStream = Files.newInputStream(path);
                 DigestInputStream digestInputStream = new DigestInputStream(inputStream, digest)) {
                digestInputStream.transferTo(OutputStream.nullOutputStream());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String extensionOf(Path path) {
        String fileName = path.getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String mimeTypeOf(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    private String titleOf(String fileName) {
        return baseNameOf(fileName);
    }

    private String baseNameOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex <= 0) {
            return fileName;
        }
        return fileName.substring(0, dotIndex);
    }

    private int resolvePage(Integer page) {
        if (page == null) {
            return 0;
        }
        if (page < 0) {
            throw new BadRequestException("page must be greater than or equal to 0");
        }
        return page;
    }

    private int resolveSize(Integer size) {
        if (size == null) {
            return 20;
        }
        if (size < 1 || size > 100) {
            throw new BadRequestException("size must be between 1 and 100");
        }
        return size;
    }

    public record PrimaryArtworkSummary(Long artworkId, String artworkPreviewUrl, String artworkFileName) {
    }

    private record ImageSize(Integer width, Integer height) {
    }

    private static final class ScanCounters {
        int totalFiles;
        int imported;
        int duplicateFiles;
        int autoBound;
        int unmatched;
        int failed;
    }
}
