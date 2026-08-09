package com.example.discord.permission;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record AuthorizationProjectionDeadLetter(String schema, String reason, String payloadHash) {
    public static final String SCHEMA = "authz.projection.dlq.v1";

    public AuthorizationProjectionDeadLetter {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported dead-letter schema");
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("dead-letter reason is required");
        if (payloadHash == null || payloadHash.isBlank()) throw new IllegalArgumentException("dead-letter payload hash is required");
    }

    public static AuthorizationProjectionDeadLetter from(String reason, String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            return new AuthorizationProjectionDeadLetter(SCHEMA, reason, java.util.HexFormat.of().formatHex(digest, 0, 8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
