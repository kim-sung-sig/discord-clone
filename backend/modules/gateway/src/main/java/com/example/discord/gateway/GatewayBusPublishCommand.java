package com.example.discord.gateway;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GatewayBusPublishCommand(
    String type,
    UUID guildId,
    UUID channelId,
    Map<String, Object> payload,
    String sourceEventId
) {
    public GatewayBusPublishCommand(String type, UUID guildId, UUID channelId, Map<String, Object> payload) {
        this(type, guildId, channelId, payload, null);
    }

    public GatewayBusPublishCommand {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        if (sourceEventId != null && sourceEventId.isBlank()) {
            throw new IllegalArgumentException("sourceEventId must not be blank");
        }
        payload = GatewayPayloadSanitizer.sanitize(Objects.requireNonNull(payload, "payload must not be null"));
    }
}
