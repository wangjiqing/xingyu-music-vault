package com.xingyu.musicvault.artwork;

import com.xingyu.musicvault.library.Track;
import com.xingyu.musicvault.library.TrackFile;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ArtworkResourceTest {
    private static final String AUTHORIZATION = "Bearer change-me";
    private static final Path ARTWORK_ROOT = Path.of("target/test-music/artworks");

    Path artworkDir;

    @BeforeEach
    @Transactional
    void cleanData() throws IOException {
        Files.createDirectories(ARTWORK_ROOT);
        artworkDir = Files.createTempDirectory(ARTWORK_ROOT, "artwork-api-test-");
        MusicArtworkBinding.deleteAll();
        Artwork.deleteAll();
        TrackFile.deleteAll();
        Track.deleteAll();
    }

    @Test
    void scanListsReadsAndBindsLocalArtwork() throws IOException {
        writePng(artworkDir.resolve("周杰伦 - 晴天.png"), 3, 2);
        writePng(artworkDir.resolve("duplicate.png"), 3, 2);
        Files.writeString(artworkDir.resolve("note.txt"), "skip");
        Long musicId = createMusic("周杰伦 - 晴天.flac", "晴天", "周杰伦");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(artworkDir))
                .when()
                .post("/api/artworks/scan")
                .then()
                .statusCode(200)
                .body("totalFiles", equalTo(2))
                .body("imported", equalTo(1))
                .body("duplicateFiles", equalTo(1))
                .body("failed", equalTo(0));

        Integer artworkId = given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks?page=0&size=20")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].fileExt", equalTo("png"))
                .body("items[0].mimeType", equalTo("image/png"))
                .body("items[0].width", equalTo(3))
                .body("items[0].height", equalTo(2))
                .body("items[0].sourceType", equalTo("local"))
                .body("items[0].previewUrl", notNullValue())
                .body("items[0].fileExists", equalTo(true))
                .body("items[0].boundCount", equalTo(1))
                .extract()
                .path("items[0].id");

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks/{id}", artworkId)
                .then()
                .statusCode(200)
                .body("id", equalTo(artworkId))
                .body("fileName", notNullValue())
                .body("fileExists", equalTo(true))
                .body("boundCount", equalTo(1))
                .body("boundTracks[0].musicId", equalTo(musicId.intValue()))
                .body("boundTracks[0].fileName", equalTo("周杰伦 - 晴天.flac"))
                .body("boundTracks[0].title", equalTo("晴天"))
                .body("boundTracks[0].artist", equalTo("周杰伦"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks/{id}/file", artworkId)
                .then()
                .statusCode(200)
                .contentType("image/png");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "artworkId": %d
                        }
                        """.formatted(artworkId))
                .when()
                .put("/api/music/{musicId}/artwork", musicId)
                .then()
                .statusCode(200)
                .body("musicId", equalTo(musicId.intValue()))
                .body("artworkStatus", equalTo("BOUND"))
                .body("artworkId", equalTo(artworkId));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music?page=0&size=20")
                .then()
                .statusCode(200)
                .body("items[0].id", equalTo(musicId.intValue()))
                .body("items[0].artworkStatus", equalTo("BOUND"))
                .body("items[0].artworkId", equalTo(artworkId))
                .body("items[0].artworkPreviewUrl", equalTo("/api/artworks/" + artworkId + "/file"))
                .body("items[0].artworkFileName", notNullValue())
                .body("items[0].artworkFileExists", equalTo(true));

        org.junit.jupiter.api.Assertions.assertEquals("matched", artworkStatusForMusic(musicId));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .delete("/api/music/{musicId}/artwork", musicId)
                .then()
                .statusCode(200)
                .body("musicId", equalTo(musicId.intValue()))
                .body("artworkStatus", equalTo("MISSING"));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{musicId}", musicId)
                .then()
                .statusCode(200)
                .body("artworkStatus", equalTo("MISSING"))
                .body("artworkId", equalTo(null));

        org.junit.jupiter.api.Assertions.assertEquals("missing", artworkStatusForMusic(musicId));
    }

    @Test
    void artworkFileIsPublicButScanStillRequiresAuthorization() throws IOException {
        Long artworkId = createArtwork("public-preview.png");

        given()
                .when()
                .get("/api/artworks/{id}/file", artworkId)
                .then()
                .statusCode(200)
                .contentType("image/png");

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/artworks/scan")
                .then()
                .statusCode(401)
                .body("error", equalTo("unauthorized"));
    }

    @Test
    void scanAutoBindsArtworkByExactBaseFileName() throws IOException {
        writePng(artworkDir.resolve("星语 - 六月的雨.png"), 4, 4);
        Long musicId = createMusic("星语 - 六月的雨.flac", "六月的雨", "星语");

        Integer artworkId = given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(artworkDir))
                .when()
                .post("/api/artworks/scan")
                .then()
                .statusCode(200)
                .body("totalFiles", equalTo(1))
                .body("imported", equalTo(1))
                .body("duplicateFiles", equalTo(0))
                .body("autoBound", equalTo(1))
                .body("unmatched", equalTo(0))
                .body("failed", equalTo(0))
                .extract()
                .path("autoBound");

        org.junit.jupiter.api.Assertions.assertEquals(1, artworkId);

        MusicArtworkBinding binding = QuarkusTransaction.requiringNew().call(() ->
                MusicArtworkBinding.<MusicArtworkBinding>find("musicId", musicId).firstResult()
        );
        org.junit.jupiter.api.Assertions.assertNotNull(binding);
        org.junit.jupiter.api.Assertions.assertEquals(musicId, binding.musicId);
        org.junit.jupiter.api.Assertions.assertEquals(ArtworkService.TRACK_COVER, binding.relationType);
        org.junit.jupiter.api.Assertions.assertTrue(binding.isPrimary);
        org.junit.jupiter.api.Assertions.assertEquals("matched", artworkStatusForMusic(musicId));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{musicId}", musicId)
                .then()
                .statusCode(200)
                .body("artworkStatus", equalTo("BOUND"))
                .body("artworkId", equalTo(binding.artworkId.intValue()))
                .body("artworkPreviewUrl", equalTo("/api/artworks/" + binding.artworkId + "/file"))
                .body("artworkFileName", equalTo("星语 - 六月的雨.png"))
                .body("artworkFileExists", equalTo(true));
    }

    @Test
    void scanDoesNotOverwriteExistingPrimaryArtworkBinding() throws IOException {
        writePng(artworkDir.resolve("星语 - 要.png"), 4, 4);
        Long musicId = createMusic("星语 - 要.flac", "要", "星语");
        Long existingArtworkId = createArtwork("existing.png");
        QuarkusTransaction.requiringNew().run(() -> {
            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = musicId;
            binding.artworkId = existingArtworkId;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(artworkDir))
                .when()
                .post("/api/artworks/scan")
                .then()
                .statusCode(200)
                .body("imported", equalTo(1))
                .body("autoBound", equalTo(0))
                .body("unmatched", equalTo(0));

        Long primaryArtworkId = QuarkusTransaction.requiringNew().call(() ->
                MusicArtworkBinding.<MusicArtworkBinding>find("musicId = ?1 and isPrimary = true", musicId)
                        .firstResult()
                        .artworkId
        );
        org.junit.jupiter.api.Assertions.assertEquals(existingArtworkId, primaryArtworkId);
    }

    @Test
    void listSupportsKeywordSearchAndBoundCount() throws IOException {
        Long musicId = createMusic("星语 - 要.flac", "要", "星语");
        Long matchedArtworkId = createArtwork("星语 - 要.png");
        createArtwork("郭静 - 下一个天亮.png");
        QuarkusTransaction.requiringNew().run(() -> {
            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = musicId;
            binding.artworkId = matchedArtworkId;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks?page=0&size=20&keyword=星语")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(matchedArtworkId.intValue()))
                .body("items[0].fileExists", equalTo(true))
                .body("items[0].boundCount", equalTo(1))
                .body("items[0].boundTracks.size()", equalTo(0));
    }

    @Test
    void listSupportsBoundStatusFilter() throws IOException {
        Long musicId = createMusic("星语 - 要.flac", "要", "星语");
        Long boundArtworkId = createArtwork("星语 - 要.png");
        Long unboundArtworkId = createArtwork("郭静 - 下一个天亮.png");
        QuarkusTransaction.requiringNew().run(() -> {
            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = musicId;
            binding.artworkId = boundArtworkId;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks?page=0&size=20&boundStatus=bound")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(boundArtworkId.intValue()));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks?page=0&size=20&boundStatus=unbound")
                .then()
                .statusCode(200)
                .body("total", equalTo(1))
                .body("items[0].id", equalTo(unboundArtworkId.intValue()));
    }

    @Test
    void importLocalArtworkSavesReusesAndBindsToMusic() throws IOException {
        Path uploadFile = Files.createTempFile(Path.of("target"), "upload-cover-", ".png");
        writePng(uploadFile, 5, 6);
        Long musicId = createMusic("刘若英 - 后来.flac", "后来", "刘若英");

        Integer artworkId = given()
                .header("Authorization", AUTHORIZATION)
                .multiPart("file", uploadFile.toFile(), "image/png")
                .when()
                .post("/api/music/{musicId}/artwork/import", musicId)
                .then()
                .statusCode(200)
                .body("musicId", equalTo(musicId.intValue()))
                .body("artworkStatus", equalTo("BOUND"))
                .body("artworkPreviewUrl", notNullValue())
                .body("artworkFileExists", equalTo(true))
                .body("artworkFileName", matchesPattern("刘若英 - 后来(-\\d+)?\\.png"))
                .extract()
                .path("artworkId");

        Artwork artwork = QuarkusTransaction.requiringNew().call(() -> Artwork.<Artwork>findById(artworkId.longValue()));
        org.junit.jupiter.api.Assertions.assertNotNull(artwork);
        org.junit.jupiter.api.Assertions.assertTrue(artwork.fileName.matches("刘若英 - 后来(-\\d+)?\\.png"));
        org.junit.jupiter.api.Assertions.assertEquals("png", artwork.fileExt);
        org.junit.jupiter.api.Assertions.assertEquals("image/png", artwork.mimeType);
        org.junit.jupiter.api.Assertions.assertEquals(5, artwork.width);
        org.junit.jupiter.api.Assertions.assertEquals(6, artwork.height);
        org.junit.jupiter.api.Assertions.assertTrue(Path.of(artwork.filePath).startsWith(ARTWORK_ROOT.toAbsolutePath()));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(Path.of(artwork.filePath)));
        org.junit.jupiter.api.Assertions.assertEquals("matched", artworkStatusForMusic(musicId));

        Long secondMusicId = createMusic("刘若英 - 很爱很爱你.flac", "很爱很爱你", "刘若英");
        given()
                .header("Authorization", AUTHORIZATION)
                .multiPart("file", uploadFile.toFile(), "image/png")
                .when()
                .post("/api/music/{musicId}/artwork/import", secondMusicId)
                .then()
                .statusCode(200)
                .body("artworkId", equalTo(artworkId))
                .body("artworkFileExists", equalTo(true))
                .body("artworkFileName", equalTo(artwork.fileName));

        Files.delete(Path.of(artwork.filePath));
        Long thirdMusicId = createMusic("刘若英 - 为爱痴狂.flac", "为爱痴狂", "刘若英");
        given()
                .header("Authorization", AUTHORIZATION)
                .multiPart("file", uploadFile.toFile(), "image/png")
                .when()
                .post("/api/music/{musicId}/artwork/import", thirdMusicId)
                .then()
                .statusCode(200)
                .body("artworkId", equalTo(artworkId))
                .body("artworkFileExists", equalTo(true));

        Artwork repairedArtwork = QuarkusTransaction.requiringNew().call(() -> Artwork.<Artwork>findById(artworkId.longValue()));
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(Path.of(repairedArtwork.filePath)));

        long artworkCount = QuarkusTransaction.requiringNew().call(() -> Artwork.<Artwork>listAll().size());
        org.junit.jupiter.api.Assertions.assertEquals(1, artworkCount);
    }

    @Test
    void importLocalArtworkRejectsUnsupportedMimeType() throws IOException {
        Path uploadFile = Files.createTempFile(Path.of("target"), "upload-cover-", ".png");
        writePng(uploadFile, 2, 2);
        Long musicId = createMusic("任贤齐 - 心太软.flac", "心太软", "任贤齐");

        given()
                .header("Authorization", AUTHORIZATION)
                .multiPart("file", uploadFile.toFile(), "text/plain")
                .when()
                .post("/api/music/{musicId}/artwork/import", musicId)
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void importLocalArtworkRejectsNonMultipartRequest() {
        Long musicId = createMusic("任贤齐 - 对面的女孩看过来.flac", "对面的女孩看过来", "任贤齐");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("{}")
                .when()
                .post("/api/music/{musicId}/artwork/import", musicId)
                .then()
                .statusCode(415)
                .body("error", equalTo("unsupported_media_type"));
    }

    @Test
    void importLocalArtworkCreatesMissingConfiguredArtworkDirectory() throws IOException {
        Path uploadFile = Files.createTempFile(Path.of("target"), "upload-cover-create-root-", ".png");
        writePng(uploadFile, 2, 2);
        Long musicId = createMusic("陈奕迅 - 十年.flac", "十年", "陈奕迅");
        deleteRecursively(ARTWORK_ROOT);
        org.junit.jupiter.api.Assertions.assertFalse(Files.exists(ARTWORK_ROOT));

        given()
                .header("Authorization", AUTHORIZATION)
                .multiPart("file", uploadFile.toFile(), "image/png")
                .when()
                .post("/api/music/{musicId}/artwork/import", musicId)
                .then()
                .statusCode(200)
                .body("artworkStatus", equalTo("BOUND"))
                .body("artworkFileExists", equalTo(true));

        org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(ARTWORK_ROOT));
    }

    @Test
    void boundArtworkReportsMissingFileState() throws IOException {
        Long musicId = createMusic("王菲 - 红豆.flac", "红豆", "王菲");
        Long artworkId = createArtwork("王菲 - 红豆.png");
        Path artworkPath = QuarkusTransaction.requiringNew().call(() ->
                Path.of(Artwork.<Artwork>findById(artworkId).filePath)
        );
        QuarkusTransaction.requiringNew().run(() -> {
            MusicArtworkBinding binding = new MusicArtworkBinding();
            binding.musicId = musicId;
            binding.artworkId = artworkId;
            binding.relationType = ArtworkService.TRACK_COVER;
            binding.isPrimary = true;
            binding.persist();
        });
        Files.delete(artworkPath);

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/music/{musicId}", musicId)
                .then()
                .statusCode(200)
                .body("artworkStatus", equalTo("BOUND"))
                .body("artworkId", equalTo(artworkId.intValue()))
                .body("artworkFileExists", equalTo(false));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks/{id}", artworkId)
                .then()
                .statusCode(200)
                .body("fileExists", equalTo(false));

        given()
                .when()
                .get("/api/artworks/{id}/file", artworkId)
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void scanRejectsDirectoryOutsideConfiguredRoot() throws IOException {
        Path outside = Files.createTempDirectory(Path.of("target"), "outside-artwork-");

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(outside))
                .when()
                .post("/api/artworks/scan")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void scanRejectsPathTraversal() {
        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(ARTWORK_ROOT.resolve("..").resolve("artworks")))
                .when()
                .post("/api/artworks/scan")
                .then()
                .statusCode(400)
                .body("error", equalTo("bad_request"));
    }

    @Test
    void scanSkipsSymlinkedFileOutsideConfiguredRoot() throws IOException {
        Path outside = Files.createTempDirectory(Path.of("target"), "outside-artwork-file-");
        Path outsideImage = outside.resolve("outside.png");
        writePng(outsideImage, 2, 2);
        Files.createSymbolicLink(artworkDir.resolve("linked.png"), outsideImage.toAbsolutePath());

        given()
                .header("Authorization", AUTHORIZATION)
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "path": "%s"
                        }
                        """.formatted(artworkDir))
                .when()
                .post("/api/artworks/scan")
                .then()
                .statusCode(200)
                .body("totalFiles", equalTo(1))
                .body("imported", equalTo(0))
                .body("failed", equalTo(1));

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks?page=0&size=20")
                .then()
                .statusCode(200)
                .body("total", equalTo(0));
    }

    @Test
    void fileEndpointRejectsStoredPathOutsideConfiguredRoot() throws IOException {
        Path outside = Files.createTempDirectory(Path.of("target"), "outside-artwork-db-");
        Path outsideImage = outside.resolve("outside.png");
        writePng(outsideImage, 2, 2);
        Long artworkId = QuarkusTransaction.requiringNew().call(() -> {
            Artwork artwork = new Artwork();
            artwork.filePath = outsideImage.toAbsolutePath().normalize().toString();
            artwork.fileName = outsideImage.getFileName().toString();
            artwork.fileExt = "png";
            artwork.mimeType = "image/png";
            artwork.fileSize = 1;
            artwork.hash = "outside-hash";
            artwork.sourceType = "local";
            artwork.sourcePath = artwork.filePath;
            artwork.persist();
            return artwork.id;
        });

        given()
                .header("Authorization", AUTHORIZATION)
                .when()
                .get("/api/artworks/{id}/file", artworkId)
                .then()
                .statusCode(404)
                .body("error", equalTo("not_found"));
    }

    private void writePng(Path path, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        ImageIO.write(image, "png", path.toFile());
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            List<Path> sortedPaths = paths.sorted(Comparator.reverseOrder()).toList();
            for (Path item : sortedPaths) {
                Files.deleteIfExists(item);
            }
        }
    }

    private Long createMusic(String fileName, String title, String artist) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Track track = new Track();
            track.title = title;
            track.normalizedTitle = title.toLowerCase();
            track.artist = artist;
            track.persist();

            TrackFile trackFile = new TrackFile();
            trackFile.trackId = track.id;
            trackFile.filePath = artworkDir.resolve(fileName).toAbsolutePath().normalize().toString();
            trackFile.fileName = fileName;
            trackFile.fileExt = "flac";
            trackFile.fileSize = 5;
            trackFile.lastModifiedAt = LocalDateTime.now();
            trackFile.persist();
            return trackFile.id;
        });
    }

    private Long createArtwork(String fileName) throws IOException {
        Path path = Files.createTempDirectory(ARTWORK_ROOT, "preexisting-artwork-").resolve(fileName);
        writePng(path, 2, 2);
        return QuarkusTransaction.requiringNew().call(() -> {
            Artwork artwork = new Artwork();
            artwork.filePath = path.toAbsolutePath().normalize().toString();
            artwork.fileName = fileName;
            artwork.fileExt = "png";
            artwork.mimeType = "image/png";
            artwork.fileSize = 1;
            artwork.hash = fileName + "-hash";
            artwork.sourceType = "local";
            artwork.sourcePath = artwork.filePath;
            artwork.title = fileName;
            artwork.persist();
            return artwork.id;
        });
    }

    private String artworkStatusForMusic(Long musicId) {
        return QuarkusTransaction.requiringNew().call(() -> {
            TrackFile trackFile = TrackFile.findById(musicId);
            Track track = Track.findById(trackFile.trackId);
            return track.artworkStatus;
        });
    }
}
