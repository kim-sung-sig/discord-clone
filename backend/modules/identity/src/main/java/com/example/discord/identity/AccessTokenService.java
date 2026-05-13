package com.example.discord.identity;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class AccessTokenService {
    private static final String HEADER = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    public AccessTokenService(String secret, Duration ttl, Clock clock) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("token secret must be at least 16 characters");
        }
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("token ttl must be positive");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue(UUID userId) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        String payload = "{\"sub\":\"" + userId + "\",\"iat\":" + issuedAt.getEpochSecond()
            + ",\"exp\":" + expiresAt.getEpochSecond() + "}";
        String unsigned = encode(HEADER) + "." + encode(payload);
        return unsigned + "." + sign(unsigned);
    }

    public AccessTokenClaims verify(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw new TokenVerificationException("access token invalid");
        }
        String unsigned = parts[0] + "." + parts[1];
        if (!constantTimeEquals(sign(unsigned), parts[2])) {
            throw new TokenVerificationException("access token invalid");
        }
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        UUID userId = UUID.fromString(extractString(payload, "sub"));
        Instant issuedAt = Instant.ofEpochSecond(extractLong(payload, "iat"));
        Instant expiresAt = Instant.ofEpochSecond(extractLong(payload, "exp"));
        if (!clock.instant().isBefore(expiresAt)) {
            throw new TokenVerificationException("access token expired");
        }
        return new AccessTokenClaims(userId, issuedAt, expiresAt);
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign access token", ex);
        }
    }

    private static String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static boolean constantTimeEquals(String left, String right) {
        return java.security.MessageDigest.isEqual(
            left.getBytes(StandardCharsets.UTF_8),
            right.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String extractString(String json, String key) {
        String marker = "\"" + key + "\":\"";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new TokenVerificationException("access token invalid");
        }
        int valueStart = start + marker.length();
        int end = json.indexOf('"', valueStart);
        return json.substring(valueStart, end);
    }

    private static long extractLong(String json, String key) {
        String marker = "\"" + key + "\":";
        int start = json.indexOf(marker);
        if (start < 0) {
            throw new TokenVerificationException("access token invalid");
        }
        int valueStart = start + marker.length();
        int end = json.indexOf(',', valueStart);
        if (end < 0) {
            end = json.indexOf('}', valueStart);
        }
        return Long.parseLong(json.substring(valueStart, end));
    }
}
