package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record WebhookMessageAuthor(UUID webhookId) implements MessageAuthor {
    public WebhookMessageAuthor {
        Objects.requireNonNull(webhookId, "webhookId must not be null");
    }
}
