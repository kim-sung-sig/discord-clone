package com.example.discord.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GatewayEvent(
    long sequence,
    String type,
    UUID guildId,
    UUID channelId,
    Map<String, Object> payload,
    Instant createdAt
) {
    public GatewayEvent {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(type, "type must not be null");
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    GatewayEvent withPayload(Map<String, Object> updatedPayload) {
        return new GatewayEvent(sequence, type, guildId, channelId, updatedPayload, createdAt);
    }

    Map<String, Object> payloadPlus(String key, Object value) {
        Map<String, Object> updated = new java.util.LinkedHashMap<>(payload);
        updated.put(key, value);
        return updated;
    }
}
