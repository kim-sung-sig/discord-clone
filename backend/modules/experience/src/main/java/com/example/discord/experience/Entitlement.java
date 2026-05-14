package com.example.discord.experience;

import java.time.Instant;
import java.util.UUID;

public record Entitlement(
    UUID id,
    UUID userId,
    UUID guildId,
    String featureKey,
    EntitlementStatus status,
    String provider,
    String providerSubscriptionId,
    Instant grantedAt,
    Instant expiresAt
) {
    public Entitlement {
        require(id, "id");
        require(userId, "userId");
        require(guildId, "guildId");
        requireText(featureKey, "featureKey");
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        requireText(provider, "provider");
        requireText(providerSubscriptionId, "providerSubscriptionId");
        if (grantedAt == null) {
            throw new IllegalArgumentException("grantedAt is required");
        }
    }

    public boolean isActiveAt(Instant instant) {
        if (instant == null) {
            throw new IllegalArgumentException("instant is required");
        }
        return status == EntitlementStatus.ACTIVE && (expiresAt == null || expiresAt.isAfter(instant));
    }

    public Entitlement withStatus(EntitlementStatus nextStatus) {
        return new Entitlement(
            id,
            userId,
            guildId,
            featureKey,
            nextStatus,
            provider,
            providerSubscriptionId,
            grantedAt,
            expiresAt
        );
    }

    private static void require(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
