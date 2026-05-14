package com.example.discord.thread;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ThreadChannel(
    UUID id,
    UUID guildId,
    UUID parentChannelId,
    UUID ownerId,
    String name,
    ThreadType type,
    boolean archived,
    int autoArchiveMinutes,
    Instant lastActivityAt,
    Instant createdAt,
    Instant updatedAt
) {
    public ThreadChannel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(parentChannelId, "parentChannelId must not be null");
        Objects.requireNonNull(ownerId, "ownerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(lastActivityAt, "lastActivityAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }
}
