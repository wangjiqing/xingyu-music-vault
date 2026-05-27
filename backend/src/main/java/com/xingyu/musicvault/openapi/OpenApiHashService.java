package com.xingyu.musicvault.openapi;

import com.xingyu.musicvault.artwork.Artwork;
import com.xingyu.musicvault.lyrics.Lyric;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@ApplicationScoped
public class OpenApiHashService {
    public String lyricsHash(Lyric lyric) {
        String material = blankToEmpty(lyric.content)
                + "\n"
                + blankToEmpty(lyric.format)
                + "\n"
                + blankToEmpty(lyric.language);
        return "sha256:" + sha256(material.getBytes(StandardCharsets.UTF_8));
    }

    public String lyricsEtag(Long trackId, String hash) {
        return quote("lyrics-" + trackId + "-" + hashSuffix(hash));
    }

    public String artworkHash(Artwork artwork, java.nio.file.Path filePath) {
        try {
            return "sha256:" + sha256(Files.readAllBytes(filePath));
        } catch (IOException exception) {
            throw new OpenApiException(jakarta.ws.rs.core.Response.Status.NOT_FOUND, "ARTWORK_NOT_FOUND", "Artwork not found");
        }
    }

    public String artworkEtag(Long trackId, String hash) {
        return quote("artwork-" + trackId + "-" + hashSuffix(hash));
    }

    public boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        String[] candidates = ifNoneMatch.split(",");
        for (String candidate : candidates) {
            String value = candidate.trim();
            if ("*".equals(value) || etag.equals(value)) {
                return true;
            }
        }
        return false;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String hashSuffix(String hash) {
        if (hash == null) {
            return "";
        }
        return hash.startsWith("sha256:") ? hash.substring("sha256:".length()) : hash;
    }

    private String quote(String value) {
        return "\"" + value + "\"";
    }

    private String blankToEmpty(String value) {
        return value == null ? "" : value;
    }
}
