package com.example.discord.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GatewayBusEvent(
    String eventId,
    String type,
    UUID guildId,
    UUID channelId,
    Map<String, Object> payload,
    Instant createdAt
) {
    public GatewayBusEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        payload = GatewayPayloadSanitizer.sanitize(Objects.requireNonNull(payload, "payload must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
