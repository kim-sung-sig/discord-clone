package com.example.discord.bot;

import java.time.Instant;
import java.util.UUID;

public record WebhookMessage(
    UUID id,
    UUID webhookId,
    UUID channelId,
    String content,
    WebhookMessageSource source,
    String actorLabel,
    Instant createdAt
) {
    public WebhookMessage {
        if (id == null || webhookId == null || channelId == null) {
            throw new IllegalArgumentException("message ids are required");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("message content is required");
        }
        if (source == null) {
            throw new IllegalArgumentException("message source is required");
        }
        if (actorLabel == null || actorLabel.isBlank()) {
            throw new IllegalArgumentException("actor label is required");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
