package com.example.discord.moderation;

import java.time.Instant;
import java.util.UUID;

public record AuditLogEntry(
    UUID id,
    UUID guildId,
    AuditLogAction action,
    UUID actorId,
    UUID targetId,
    String reason,
    Instant createdAt
) {
    public AuditLogEntry {
        if (id == null) {
            throw new IllegalArgumentException("audit id is required");
        }
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (action == null) {
            throw new IllegalArgumentException("audit action is required");
        }
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
