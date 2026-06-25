package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record PinMessageRequest(
    UUID messageId,
    MessageAuthor requester,
    boolean pinned
) {
    public PinMessageRequest {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(requester, "requester must not be null");
    }
}
