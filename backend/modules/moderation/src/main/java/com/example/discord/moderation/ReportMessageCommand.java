package com.example.discord.moderation;

import java.util.UUID;

public record ReportMessageCommand(
    UUID guildId,
    UUID channelId,
    UUID messageId,
    UUID reporterId,
    String reason
) {
    public ReportMessageCommand {
        require(guildId, "guildId");
        require(channelId, "channelId");
        require(messageId, "messageId");
        require(reporterId, "reporterId");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason is required");
        }
        reason = reason.trim();
    }

    private static void require(UUID value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
