package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record DeleteMessageRequest(
    UUID messageId,
    MessageAuthor requester
) {
    public DeleteMessageRequest {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(requester, "requester must not be null");
    }
}
