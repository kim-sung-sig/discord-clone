package com.example.discord.message;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MessageDeliveryPayload {
    private MessageDeliveryPayload() {
    }

    public static Map<String, Object> messageCreate(Message message, String authorDisplayName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.id().toString());
        payload.put("guildId", message.guildId().toString());
        payload.put("channelId", message.channelId().toString());
        payload.put("authorId", message.authorId().toString());
        if (authorDisplayName != null) {
            // GatewayEvent copies the payload with Map.copyOf, which rejects null values; omit unknown authors.
            payload.put("authorDisplayName", authorDisplayName);
        }
        payload.put("content", message.content().value());
        payload.put("mentions", MessageMentionTokens.tokens(message.mentions()));
        payload.put("pinned", message.pinned());
        payload.put("deleted", message.deleted());
        payload.put("edited", message.edited());
        payload.put("createdAt", message.createdAt().toString());
        payload.put("updatedAt", message.updatedAt().toString());
        return payload;
    }
}
