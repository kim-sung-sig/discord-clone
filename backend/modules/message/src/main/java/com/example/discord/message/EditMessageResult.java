package com.example.discord.message;

import java.util.Objects;

public record EditMessageResult(Message message) {
    public EditMessageResult {
        Objects.requireNonNull(message, "message must not be null");
    }
}
