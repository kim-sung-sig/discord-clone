package com.example.discord.moderation;

import java.time.Instant;
import java.util.UUID;

public record SecurityAlert(
    UUID id,
    UUID guildId,
    UUID actorId,
    UUID targetId,
    String type,
    String severity,
    String reason,
    Instant createdAt
) {
    public SecurityAlert {
        if (id == null) {
            throw new IllegalArgumentException("alert id is required");
        }
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("targetId is required");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (severity == null || severity.isBlank()) {
            throw new IllegalArgumentException("severity is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
    }
}
