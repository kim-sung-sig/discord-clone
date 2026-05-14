package com.example.discord.experience;

import java.time.Instant;
import java.util.UUID;

public record BillingCheckoutCommand(
    UUID userId,
    UUID guildId,
    String featureKey,
    String providerSubscriptionId,
    Instant expiresAt,
    boolean simulateProviderFailure
) {
    public BillingCheckoutCommand {
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (featureKey == null || featureKey.isBlank()) {
            throw new IllegalArgumentException("featureKey is required");
        }
        if (providerSubscriptionId == null || providerSubscriptionId.isBlank()) {
            throw new IllegalArgumentException("providerSubscriptionId is required");
        }
    }
}
