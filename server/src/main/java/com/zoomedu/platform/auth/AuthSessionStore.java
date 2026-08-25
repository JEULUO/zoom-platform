package com.zoomedu.platform.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
class AuthSessionStore {

    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String REVOKED_ACCESS_PREFIX = "auth:revoked-access:";

    private final ObjectMapper objectMapper;
    private final SecurityProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private final StringRedisTemplate redisTemplate;

    AuthSessionStore(
            ObjectMapper objectMapper,
            SecurityProperties properties,
            StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }

    String createRefreshToken(AuthenticatedUser user) {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        String value = writeSession(new RefreshSession(user.id(), user.username()));
        redisTemplate.opsForValue().set(refreshKey(token), value, properties.refreshTokenTtl());
        return token;
    }

    Optional<RefreshSession> consumeRefreshToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String value = redisTemplate.opsForValue().getAndDelete(refreshKey(token));
        return value == null ? Optional.empty() : Optional.of(readSession(value));
    }

    void revokeRefreshToken(String token) {
        if (token != null && !token.isBlank()) {
            redisTemplate.delete(refreshKey(token));
        }
    }

    void revokeAccessToken(String jwtId, Instant expiresAt, Instant now) {
        if (jwtId == null || expiresAt == null || !expiresAt.isAfter(now)) {
            return;
        }
        redisTemplate.opsForValue().set(
                REVOKED_ACCESS_PREFIX + jwtId,
                "1",
                Duration.between(now, expiresAt));
    }

    boolean isAccessTokenRevoked(String jwtId) {
        return jwtId != null && Boolean.TRUE.equals(redisTemplate.hasKey(REVOKED_ACCESS_PREFIX + jwtId));
    }

    private String refreshKey(String token) {
        return REFRESH_PREFIX + sha256(token);
    }

    private String writeSession(RefreshSession session) {
        try {
            return objectMapper.writeValueAsString(session);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize refresh session", exception);
        }
    }

    private RefreshSession readSession(String value) {
        try {
            return objectMapper.readValue(value, RefreshSession.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize refresh session", exception);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
