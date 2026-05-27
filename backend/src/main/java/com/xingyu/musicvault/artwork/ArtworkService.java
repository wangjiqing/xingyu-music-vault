package com.xingyu.musicvault.artwork;

import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.BoundTrackResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.ArtworkScanResponse;
import com.xingyu.musicvault.artwork.ArtworkDtos.MusicArtworkResponse;
import com.xingyu.musicvault.common.PageResponse;
import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import com.xingyu.musicvault.openapi.OpenApiChangeLogService;
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
import org.jboss.resteasy.reactive.multipart.FileUpload;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
    private static final long MAX_IMPORT_FILE_SIZE = 10L * 1024L * 1024L;
    private static final int MAX_SAFE_FILE_NAME_BASE_LENGTH = 120;

    @ConfigProperty(name = "app.artwork.scan-dir")
    String defaultScanDir;

    @Inject
    ArtworkRepository artworkRepository;

    @Inject
    MusicArtworkBindingRepository bindingRepository;

    @Inject
    OpenApiChangeLogService openApiChangeLogService;

    @Transactional
    public ArtworkScanResponse scan(String requestedPath) {
        Path configuredRoot = configuredArtworkRoot(true, true);
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

    public PageResponse<ArtworkResponse> list(Integer page, Integer size, String keyword, String boundStatus) {
        int pageValue = resolvePage(page);
        int sizeValue = resolveSize(size);
        PanacheQuery<Artwork> query = artworkListQuery(keyword, boundStatus);
        long total = query.count();
        List<Artwork> artworks = query.page(Page.of(pageValue, sizeValue)).list();
        Map<Long, Long> boundCounts = boundCountsByArtworkId(artworks);
        List<ArtworkResponse> items = artworks.stream()
                .map(artwork -> ArtworkResponse.from(
                        artwork,
                        artworkFileExists(artwork),
                        boundCounts.getOrDefault(artwork.id, 0L),
                        List.of()
                ))
                .toList();
        return new PageResponse<>(items, pageValue, sizeValue, total);
    }

    public ArtworkResponse get(Long id) {
        Artwork artwork = findArtwork(id);
        List<BoundTrackResponse> boundTracks = boundTracksForArtwork(id);
        return ArtworkResponse.from(artwork, artworkFileExists(artwork), boundTracks.size(), boundTracks);
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
        TrackFile trackFile = findTrackFile(musicId);
        Artwork artwork = findArtwork(artworkId);
        if (!artworkFileExists(artwork)) {
            throw new BadRequestException("Artwork file is missing");
        }

        bindPrimaryArtwork(trackFile, artwork);
        return toMusicArtworkResponse(musicId, artwork);
    }

    @Transactional
    public MusicArtworkResponse importAndBind(Long musicId, FileUpload upload) {
        TrackFile trackFile = findTrackFile(musicId);
        if (upload == null || upload.uploadedFile() == null) {
            throw new BadRequestException("file is required");
        }

        Path uploadedFile = upload.uploadedFile();
        String extension = extensionOf(upload.fileName());
        validateImportFile(uploadedFile, extension, upload.contentType());
        String hash;
        ImageSize imageSize;
        try {
            hash = sha256(uploadedFile);
            imageSize = readImageSize(uploadedFile);
        } catch (IOException exception) {
            throw new BadRequestException("Uploaded artwork is not a readable image", exception);
        }

        Artwork artwork = artworkRepository.findByHash(hash);
        Path importedFile = null;
        try {
            if (artwork == null || !artworkFileExists(artwork)) {
                Path storageRoot = configuredArtworkRoot(true, true);
                Path target = uniqueArtworkTarget(storageRoot, safeArtworkBaseName(trackFile), extension);
                Files.copy(uploadedFile, target, StandardCopyOption.COPY_ATTRIBUTES);
                importedFile = target;

                if (artwork == null) {
                    artwork = new Artwork();
                    artwork.hash = hash;
                    artwork.sourceType = "local";
                }
                updateArtworkFileMetadata(artwork, target, extension, imageSize);
                if (!artwork.isPersistent()) {
                    artwork.persist();
                }
            }

            bindPrimaryArtwork(trackFile, artwork);
            return toMusicArtworkResponse(musicId, artwork);
        } catch (IOException exception) {
            deleteImportedFileQuietly(importedFile);
            throw new BadRequestException("Failed to save uploaded artwork: " + exception.getMessage(), exception);
        } catch (RuntimeException exception) {
            deleteImportedFileQuietly(importedFile);
            throw exception;
        }
    }

    private void bindPrimaryArtwork(TrackFile trackFile, Artwork artwork) {
        MusicArtworkBinding primary = bindingRepository.findPrimaryTrackCoverByMusicId(trackFile.id);
        if (primary != null && Objects.equals(primary.artworkId, artwork.id)) {
            updateTrackArtworkStatus(trackFile, "matched");
            return;
        }
        if (primary != null) {
            primary.isPrimary = false;
        }

        MusicArtworkBinding binding = bindingRepository.findTrackCoverByMusicIdAndArtworkId(trackFile.id, artwork.id);
        if (binding == null) {
            binding = new MusicArtworkBinding();
            binding.musicId = trackFile.id;
            binding.artworkId = artwork.id;
            binding.relationType = TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        } else {
            binding.isPrimary = true;
        }
        updateTrackArtworkStatus(trackFile, "matched");
        openApiChangeLogService.recordArtworkChange(trackFile.id);
    }

    private void updateArtworkFileMetadata(Artwork artwork, Path path, String extension, ImageSize imageSize) throws IOException {
        artwork.filePath = path.toString();
        artwork.fileName = path.getFileName().toString();
        artwork.fileExt = extension;
        artwork.mimeType = mimeTypeOf(extension);
        artwork.fileSize = Files.size(path);
        artwork.width = imageSize.width();
        artwork.height = imageSize.height();
        artwork.sourcePath = artwork.filePath;
        artwork.title = titleOf(artwork.fileName);
    }

    @Transactional
    public MusicArtworkResponse unbind(Long musicId) {
        TrackFile trackFile = findTrackFile(musicId);
        MusicArtworkBinding primary = bindingRepository.findPrimaryTrackCoverByMusicId(musicId);
        if (primary != null) {
            primary.delete();
        }
        updateTrackArtworkStatus(trackFile, "missing");
        openApiChangeLogService.recordArtworkChange(musicId);
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
                    artwork.fileName,
                    artworkFileExists(artwork)
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
            openApiChangeLogService.recordArtworkChange(trackFile.id);
            counters.autoBound++;
        }
    }

    private Map<String, List<TrackFile>> trackFilesByBaseName() {
        return TrackFile.<TrackFile>listAll().stream()
                .collect(Collectors.groupingBy(this::baseNameOf));
    }

    private PanacheQuery<Artwork> artworkListQuery(String keyword, String boundStatus) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedBoundStatus = normalizeBoundStatus(boundStatus);
        String boundFilter = switch (normalizedBoundStatus) {
            case "bound" -> "id in (select binding.artworkId from MusicArtworkBinding binding where binding.relationType = 'track_cover' and binding.isPrimary = true)";
            case "unbound" -> "id not in (select binding.artworkId from MusicArtworkBinding binding where binding.relationType = 'track_cover' and binding.isPrimary = true)";
            default -> null;
        };
        if (normalizedKeyword == null) {
            if (boundFilter == null) {
                return Artwork.findAll(Sort.descending("createdAt"));
            }
            return Artwork.find(boundFilter, Sort.descending("createdAt"));
        }
        String likeKeyword = "%" + normalizedKeyword + "%";
        String keywordFilter = "(lower(fileName) like ?1 or lower(title) like ?1)";
        if (boundFilter == null) {
            return Artwork.find(keywordFilter, Sort.descending("createdAt"), likeKeyword);
        }
        return Artwork.find(
                keywordFilter + " and " + boundFilter,
                Sort.descending("createdAt"),
                likeKeyword
        );
    }

    private Map<Long, Long> boundCountsByArtworkId(List<Artwork> artworks) {
        if (artworks == null || artworks.isEmpty()) {
            return Map.of();
        }
        List<Long> artworkIds = artworks.stream().map(artwork -> artwork.id).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : bindingRepository.countPrimaryTrackCoversByArtworkIds(artworkIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private List<BoundTrackResponse> boundTracksForArtwork(Long artworkId) {
        List<MusicArtworkBinding> bindings = bindingRepository.findPrimaryTrackCoversByArtworkId(artworkId);
        if (bindings.isEmpty()) {
            return List.of();
        }
        List<Long> musicIds = bindings.stream().map(binding -> binding.musicId).distinct().toList();
        Map<Long, TrackFile> trackFilesById = TrackFile.<TrackFile>list("id in ?1", musicIds).stream()
                .collect(Collectors.toMap(trackFile -> trackFile.id, Function.identity()));
        Map<Long, Track> tracksById = tracksById(trackFilesById.values().stream().toList());

        return musicIds.stream()
                .map(trackFilesById::get)
                .filter(Objects::nonNull)
                .map(trackFile -> toBoundTrackResponse(trackFile, tracksById.get(trackFile.trackId)))
                .toList();
    }

    private Map<Long, Track> tracksById(List<TrackFile> trackFiles) {
        List<Long> trackIds = trackFiles.stream()
                .map(trackFile -> trackFile.trackId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        return Track.<Track>list("id in ?1", trackIds).stream()
                .collect(Collectors.toMap(track -> track.id, Function.identity()));
    }

    private BoundTrackResponse toBoundTrackResponse(TrackFile trackFile, Track track) {
        return new BoundTrackResponse(
                trackFile.id,
                trackFile.trackId,
                trackFile.fileName,
                trackFile.filePath,
                track == null ? null : track.title,
                track == null ? null : track.artist
        );
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBoundStatus(String boundStatus) {
        if (boundStatus == null || boundStatus.isBlank()) {
            return "all";
        }
        String normalized = boundStatus.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("all", "bound", "unbound").contains(normalized)) {
            throw new BadRequestException("boundStatus must be one of: all, bound, unbound");
        }
        return normalized;
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

    private TrackFile findTrackFile(Long musicId) {
        TrackFile trackFile = TrackFile.findById(musicId);
        if (trackFile == null) {
            throw new NotFoundException("Music not found");
        }
        return trackFile;
    }

    private void validateImportFile(Path uploadedFile, String extension, String contentType) {
        if (!Files.isRegularFile(uploadedFile) || !Files.isReadable(uploadedFile)) {
            throw new BadRequestException("Uploaded artwork file is not readable");
        }
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new BadRequestException("Unsupported artwork file extension: " + extension);
        }
        String normalizedContentType = normalizeContentType(contentType);
        if (!isSupportedMimeType(normalizedContentType)) {
            throw new BadRequestException("Unsupported artwork content type: " + contentType);
        }
        String expectedMimeType = mimeTypeOf(extension);
        if (!expectedMimeType.equals(normalizedContentType)) {
            throw new BadRequestException("Artwork file extension does not match content type");
        }
        try {
            long size = Files.size(uploadedFile);
            if (size < 1) {
                throw new BadRequestException("Uploaded artwork file is empty");
            }
            if (size > MAX_IMPORT_FILE_SIZE) {
                throw new BadRequestException("Uploaded artwork file must be 10MB or smaller");
            }
            String detectedExtension = detectedImageExtension(uploadedFile);
            if (detectedExtension == null) {
                throw new BadRequestException("Uploaded artwork file is not a supported image");
            }
            if (!extensionMatchesDetectedImage(extension, detectedExtension)) {
                throw new BadRequestException("Artwork file extension does not match image content");
            }
        } catch (IOException exception) {
            throw new BadRequestException("Uploaded artwork file size cannot be read", exception);
        }
    }

    private boolean isSupportedMimeType(String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType);
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "";
        }
        int semicolonIndex = contentType.indexOf(';');
        String rawContentType = semicolonIndex < 0 ? contentType : contentType.substring(0, semicolonIndex);
        return rawContentType.trim().toLowerCase(Locale.ROOT);
    }

    private Path uniqueArtworkTarget(Path storageRoot, String baseName, String extension) {
        Path safeRoot = storageRoot.toAbsolutePath().normalize();
        for (int index = 0; index < 1000; index++) {
            String suffix = index == 0 ? "" : "-" + index;
            Path target = safeRoot.resolve(baseName + suffix + "." + extension).normalize();
            if (!target.startsWith(safeRoot)) {
                throw new BadRequestException("Artwork file name is invalid");
            }
            if (!Files.exists(target)) {
                return target;
            }
        }
        throw new BadRequestException("Cannot allocate artwork file name");
    }

    private String safeArtworkBaseName(TrackFile trackFile) {
        Track track = trackFile.trackId == null ? null : Track.findById(trackFile.trackId);
        String title = track == null ? null : track.title;
        String artist = track == null ? null : track.artist;
        String baseName;
        if (artist != null && !artist.isBlank() && title != null && !title.isBlank()) {
            baseName = artist + " - " + title;
        } else if (title != null && !title.isBlank()) {
            baseName = title;
        } else {
            baseName = baseNameOf(trackFile);
        }
        baseName = sanitizeFileNameBase(baseName);
        if (baseName.isBlank()) {
            return "artwork-" + trackFile.id;
        }
        if (baseName.length() > MAX_SAFE_FILE_NAME_BASE_LENGTH) {
            return baseName.substring(0, MAX_SAFE_FILE_NAME_BASE_LENGTH).strip();
        }
        return baseName;
    }

    private String sanitizeFileNameBase(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isISOControl(character) || "/\\:*?\"<>|".indexOf(character) >= 0) {
                continue;
            }
            builder.append(character);
        }
        return builder.toString().strip();
    }

    private void deleteImportedFileQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            LOG.warnf(exception, "Failed to clean up imported artwork file: path=%s", path);
        }
    }

    private MusicArtworkResponse toMusicArtworkResponse(Long musicId, Artwork artwork) {
        return new MusicArtworkResponse(
                musicId,
                "BOUND",
                artwork.id,
                "/api/artworks/" + artwork.id + "/file",
                artwork.fileName,
                artworkFileExists(artwork)
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

    private boolean artworkFileExists(Artwork artwork) {
        try {
            Path path = resolveArtworkFile(artwork);
            return Files.isRegularFile(path) && Files.isReadable(path);
        } catch (NotFoundException exception) {
            return false;
        }
    }

    private Path resolveArtworkFile(Artwork artwork) {
        try {
            Path configuredRoot = configuredArtworkRoot(false, false);
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
        } catch (BadRequestException exception) {
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
        Path realPath = toUsableDirectory(requested, false, true);
        if (!realPath.startsWith(configuredRoot)) {
            throw new BadRequestException("Artwork directory is outside configured scan root: " + requested);
        }
        return realPath;
    }

    private Path configuredArtworkRoot(boolean createIfMissing, boolean requireWritable) {
        if (defaultScanDir == null || defaultScanDir.isBlank()) {
            throw new BadRequestException("No artwork scan directory configured");
        }
        return toUsableDirectory(Path.of(defaultScanDir), createIfMissing, requireWritable);
    }

    private Path toUsableDirectory(Path path, boolean createIfMissing, boolean requireWritable) {
        if (!Files.exists(path)) {
            if (!createIfMissing) {
                throw new BadRequestException("Artwork directory does not exist: " + path);
            }
            try {
                Files.createDirectories(path);
            } catch (IOException exception) {
                throw new BadRequestException("Artwork directory cannot be created: " + path, exception);
            }
        }
        if (!Files.isDirectory(path)) {
            throw new BadRequestException("Artwork path is not a directory: " + path);
        }
        if (!Files.isReadable(path)) {
            throw new BadRequestException("Artwork directory is not readable: " + path);
        }
        if (requireWritable && !Files.isWritable(path)) {
            throw new BadRequestException("Artwork directory is not writable: " + path);
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
        return detectedImageExtension(path) != null;
    }

    private boolean extensionMatchesDetectedImage(String extension, String detectedExtension) {
        if ("jpg".equals(detectedExtension)) {
            return "jpg".equals(extension) || "jpeg".equals(extension);
        }
        return detectedExtension.equals(extension);
    }

    private String detectedImageExtension(Path path) throws IOException {
        byte[] header = new byte[12];
        int read;
        try (InputStream inputStream = Files.newInputStream(path)) {
            read = inputStream.read(header);
        }
        if (read < 4) {
            return null;
        }
        boolean jpeg = (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8;
        if (jpeg) {
            return "jpg";
        }
        boolean png = read >= 8
                && (header[0] & 0xff) == 0x89
                && header[1] == 'P'
                && header[2] == 'N'
                && header[3] == 'G'
                && (header[4] & 0xff) == 0x0d
                && (header[5] & 0xff) == 0x0a
                && (header[6] & 0xff) == 0x1a
                && (header[7] & 0xff) == 0x0a;
        if (png) {
            return "png";
        }
        boolean webp = read >= 12
                && header[0] == 'R'
                && header[1] == 'I'
                && header[2] == 'F'
                && header[3] == 'F'
                && header[8] == 'W'
                && header[9] == 'E'
                && header[10] == 'B'
                && header[11] == 'P';
        if (webp) {
            return "webp";
        }
        return null;
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
        return extensionOf(path.getFileName().toString());
    }

    private String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "";
        }
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

    public record PrimaryArtworkSummary(
            Long artworkId,
            String artworkPreviewUrl,
            String artworkFileName,
            boolean artworkFileExists
    ) {
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
