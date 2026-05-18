package com.example.discord.moderation;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public record MessageReport(
    UUID id,
    UUID guildId,
    UUID channelId,
    UUID messageId,
    UUID reporterId,
    String reason,
    MessageReportStatus status,
    Optional<UUID> moderatorId,
    String resolution,
    Instant createdAt,
    Instant updatedAt
) {
    public MessageReport {
        require(id, "id");
        require(guildId, "guildId");
        require(channelId, "channelId");
        require(messageId, "messageId");
        require(reporterId, "reporterId");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        if (status == null) {
            throw new IllegalArgumentException("status is required");
        }
        moderatorId = moderatorId == null ? Optional.empty() : moderatorId;
        resolution = resolution == null ? "" : resolution;
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = createdAt;
        }
    }

    private static void require(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
