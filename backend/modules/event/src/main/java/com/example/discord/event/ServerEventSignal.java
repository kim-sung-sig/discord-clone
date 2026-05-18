package com.example.discord.event;

import java.time.Instant;
import java.util.UUID;

public record ServerEventSignal(UUID id, UUID guildId, UUID eventId, UUID actorId, ServerEventSignalType type, String detail, Instant createdAt) {
    public ServerEventSignal {
        if (id == null || guildId == null || eventId == null || type == null) {
            throw new IllegalArgumentException("signal fields are required");
        }
        detail = detail == null ? "" : detail;
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
