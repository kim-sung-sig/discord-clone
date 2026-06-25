package com.example.discord.message;

import java.util.Objects;

public record PublishMessageResult(Message message) {
    public PublishMessageResult {
        Objects.requireNonNull(message, "message must not be null");
    }
}
