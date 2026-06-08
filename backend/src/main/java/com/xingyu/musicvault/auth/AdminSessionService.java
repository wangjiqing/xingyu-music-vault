package com.xingyu.musicvault.auth;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Cookie;
import jakarta.ws.rs.core.HttpHeaders;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class AdminSessionService {
    public static final String COOKIE_NAME = "XINGYU_MUSIC_VAULT_SESSION";

    private static final int SESSION_ID_BYTES = 32;

    @Inject
    AdminAuthConfig config;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, AdminSession> sessions = new ConcurrentHashMap<>();

    public String createSession(AdminUser user) {
        String sessionId = newSessionId();
        Instant now = Instant.now();
        sessions.put(sessionId, new AdminSession(user.id, now.plus(sessionTtl())));
        return sessionId;
    }

    public Optional<Long> findUserId(HttpHeaders headers) {
        Cookie cookie = headers.getCookies().get(COOKIE_NAME);
        if (cookie == null || cookie.getValue() == null || cookie.getValue().isBlank()) {
            return Optional.empty();
        }
        return findUserId(cookie.getValue());
    }

    public Optional<Long> findUserId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        AdminSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }
        if (session.expiresAt().isBefore(Instant.now())) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        return Optional.of(session.userId());
    }

    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    public void clearAll() {
        sessions.clear();
    }

    public int cookieMaxAgeSeconds() {
        return Math.toIntExact(sessionTtl().toSeconds());
    }

    private Duration sessionTtl() {
        return Duration.ofMinutes(config.session().ttlMinutes());
    }

    private String newSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private record AdminSession(Long userId, Instant expiresAt) {
    }
}
