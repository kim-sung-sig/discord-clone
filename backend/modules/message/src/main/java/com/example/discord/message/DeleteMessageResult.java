package com.example.discord.message;

import java.util.Objects;

public record DeleteMessageResult(Message message) {
    public DeleteMessageResult {
        Objects.requireNonNull(message, "message must not be null");
    }
}
