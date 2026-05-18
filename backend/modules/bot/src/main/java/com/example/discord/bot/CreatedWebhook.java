package com.example.discord.bot;

public record CreatedWebhook(Webhook webhook, String token) {
    public CreatedWebhook {
        if (webhook == null) {
            throw new IllegalArgumentException("webhook is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("webhook token is required");
        }
    }
}
