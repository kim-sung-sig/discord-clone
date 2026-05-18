package com.example.discord.notification;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record MentionNotificationCommand(
    UUID guildId,
    UUID channelId,
    UUID messageId,
    long sequence,
    UUID authorId,
    Set<UUID> mentionedUserIds,
    Set<UUID> visibleRecipientIds,
    String summary
) {
    public MentionNotificationCommand {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(authorId, "authorId must not be null");
        mentionedUserIds = Set.copyOf(Objects.requireNonNull(mentionedUserIds, "mentionedUserIds must not be null"));
        visibleRecipientIds = Set.copyOf(Objects.requireNonNull(visibleRecipientIds, "visibleRecipientIds must not be null"));
        summary = Objects.requireNonNull(summary, "summary must not be null");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
    }
}
