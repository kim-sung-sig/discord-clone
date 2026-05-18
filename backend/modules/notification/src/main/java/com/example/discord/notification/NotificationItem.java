package com.example.discord.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record NotificationItem(
    UUID id,
    UUID userId,
    UUID guildId,
    UUID channelId,
    UUID sourceId,
    long sequence,
    NotificationKind kind,
    String summary,
    boolean read,
    Instant createdAt
) {
    public NotificationItem {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        summary = Objects.requireNonNull(summary, "summary must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
    }

    NotificationItem markRead() {
        return new NotificationItem(id, userId, guildId, channelId, sourceId, sequence, kind, summary, true, createdAt);
    }
}
