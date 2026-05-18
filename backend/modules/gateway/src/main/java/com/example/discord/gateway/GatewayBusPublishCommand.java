package com.example.discord.gateway;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record GatewayBusPublishCommand(
    String type,
    UUID guildId,
    UUID channelId,
    Map<String, Object> payload
) {
    public GatewayBusPublishCommand {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        payload = GatewayPayloadSanitizer.sanitize(Objects.requireNonNull(payload, "payload must not be null"));
    }
}
