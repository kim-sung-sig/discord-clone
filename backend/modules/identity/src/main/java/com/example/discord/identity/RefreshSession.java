package com.example.discord.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record RefreshSession(
    UUID id,
    UUID userId,
    String tokenHash,
    String deviceName,
    Instant createdAt,
    Instant expiresAt,
    Optional<Instant> revokedAt
) {
    public static RefreshSession create(
        UUID id,
        UUID userId,
        String tokenHash,
        String deviceName,
        Instant createdAt,
        Instant expiresAt
    ) {
        return new RefreshSession(id, userId, tokenHash, deviceName, createdAt, expiresAt, Optional.empty());
    }

    public boolean revoked() {
        return revokedAt.isPresent();
    }

    public boolean expiredAt(Instant instant) {
        return !instant.isBefore(expiresAt);
    }

    public Rotation rotate(UUID nextSessionId, String nextTokenHash, Instant rotatedAt, Instant nextExpiresAt) {
        if (revoked()) {
            throw new IllegalStateException("refresh session revoked");
        }
        if (expiredAt(rotatedAt)) {
            throw new IllegalStateException("refresh session expired");
        }
        RefreshSession revokedPrevious = new RefreshSession(
            id,
            userId,
            tokenHash,
            deviceName,
            createdAt,
            expiresAt,
            Optional.of(rotatedAt)
        );
        RefreshSession next = create(nextSessionId, userId, nextTokenHash, deviceName, rotatedAt, nextExpiresAt);
        return new Rotation(revokedPrevious, next);
    }

    public record Rotation(RefreshSession revokedPrevious, RefreshSession next) {
    }
}
