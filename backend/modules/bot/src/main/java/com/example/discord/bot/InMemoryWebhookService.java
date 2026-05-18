package com.example.discord.bot;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryWebhookService {
    private final Map<UUID, StoredWebhook> webhooks = new LinkedHashMap<>();
    private final List<WebhookAuditEvent> auditEvents = new ArrayList<>();

    public synchronized CreatedWebhook createWebhook(
        UUID guildId,
        UUID channelId,
        UUID actorId,
        String name,
        boolean canManageWebhooks
    ) {
        if (!canManageWebhooks) {
            throw new SecurityException("webhook management permission is required");
        }
        Webhook webhook = new Webhook(UUID.randomUUID(), guildId, channelId, actorId, name, Instant.now());
        String token = "wh_" + UUID.randomUUID().toString().replace("-", "");
        webhooks.put(webhook.id(), new StoredWebhook(webhook, sha256(token)));
        appendAudit(guildId, actorId, webhook.id(), WebhookAuditAction.WEBHOOK_CREATED);
        return new CreatedWebhook(webhook, token);
    }

    public synchronized Webhook webhook(UUID webhookId) {
        StoredWebhook stored = webhooks.get(webhookId);
        if (stored == null) {
            throw new IllegalArgumentException("webhook not found");
        }
        return stored.webhook();
    }

    public synchronized WebhookMessage sendWebhook(
        UUID webhookId,
        String token,
        String content,
        boolean canSendWebhooks
    ) {
        if (!canSendWebhooks) {
            throw new SecurityException("webhook send permission is required");
        }
        StoredWebhook stored = webhooks.get(webhookId);
        if (stored == null || !stored.tokenHash().equals(sha256(token))) {
            throw new SecurityException("webhook token is invalid");
        }
        Webhook webhook = stored.webhook();
        WebhookMessage message = new WebhookMessage(
            UUID.randomUUID(),
            webhook.id(),
            webhook.channelId(),
            content,
            WebhookMessageSource.WEBHOOK,
            "webhook:" + webhook.name(),
            Instant.now()
        );
        appendAudit(webhook.guildId(), webhook.creatorId(), webhook.id(), WebhookAuditAction.WEBHOOK_SENT);
        return message;
    }

    public synchronized List<WebhookAuditEvent> auditEvents() {
        return auditEvents.stream()
            .sorted(Comparator.comparing(WebhookAuditEvent::createdAt).reversed())
            .toList();
    }

    private void appendAudit(UUID guildId, UUID actorId, UUID webhookId, WebhookAuditAction action) {
        auditEvents.add(new WebhookAuditEvent(UUID.randomUUID(), guildId, actorId, webhookId, action, Instant.now()));
    }

    private static String sha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record StoredWebhook(Webhook webhook, String tokenHash) {
    }
}
