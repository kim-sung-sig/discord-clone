package com.example.discord.permission;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationWatermarkAdvanced(
    UUID eventId,
    UUID guildId,
    long permissionVersion,
    AuthorizationAudience audience
) {
    public AuthorizationWatermarkAdvanced {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(audience, "audience must not be null");
        if (permissionVersion < 0) {
            throw new IllegalArgumentException("permissionVersion must not be negative");
        }
    }
}
