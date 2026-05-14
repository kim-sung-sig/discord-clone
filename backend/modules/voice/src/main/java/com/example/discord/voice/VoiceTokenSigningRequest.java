package com.example.discord.voice;

import java.time.Instant;
import java.util.UUID;

public record VoiceTokenSigningRequest(
    UUID guildId,
    UUID channelId,
    UUID userId,
    Instant issuedAt,
    long ttlSeconds
) {
    public VoiceTokenSigningRequest {
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (issuedAt == null) {
            throw new IllegalArgumentException("issuedAt is required");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
    }
}
