package com.example.discord.bot;

import java.time.Instant;
import java.util.UUID;

public record WebhookAuditEvent(UUID id, UUID guildId, UUID actorId, UUID webhookId, WebhookAuditAction action, Instant createdAt) {
    public WebhookAuditEvent {
        if (id == null || guildId == null || webhookId == null || action == null) {
            throw new IllegalArgumentException("audit fields are required");
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
