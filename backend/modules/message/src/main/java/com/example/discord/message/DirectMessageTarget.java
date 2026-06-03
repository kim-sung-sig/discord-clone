package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record DirectMessageTarget(
    UUID conversationId,
    UUID recipientId
) implements MessageTarget {
    public DirectMessageTarget {
        Objects.requireNonNull(conversationId, "conversationId must not be null");
        Objects.requireNonNull(recipientId, "recipientId must not be null");
    }
}
