package com.example.discord.message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MessagePublished(
    UUID eventId,
    UUID messageId,
    MessageAuthor author,
    MessageTarget target,
    List<MessageMentionTarget> mentions,
    String correlationId,
    Instant occurredAt
) {
    public MessagePublished {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(target, "target must not be null");
        mentions = List.copyOf(Objects.requireNonNull(mentions, "mentions must not be null"));
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
    }
}
