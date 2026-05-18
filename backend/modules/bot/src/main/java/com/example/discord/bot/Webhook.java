package com.example.discord.bot;

import java.time.Instant;
import java.util.UUID;

public record Webhook(UUID id, UUID guildId, UUID channelId, UUID creatorId, String name, Instant createdAt) {
    public Webhook {
        if (id == null || guildId == null || channelId == null || creatorId == null) {
            throw new IllegalArgumentException("webhook ids are required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("webhook name is required");
        }
        name = name.trim();
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
