package com.example.discord.message;

import java.util.List;
import java.util.Objects;

public record PublishMessageRequest(
    MessageAuthor author,
    MessageTarget target,
    MessageContent content,
    List<MessageMentionTarget> mentions,
    IdempotencyKey idempotencyKey,
    String correlationId
) {
    public PublishMessageRequest {
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(content, "content must not be null");
        mentions = List.copyOf(Objects.requireNonNull(mentions, "mentions must not be null"));
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
    }
}
