package com.example.discord.message;

import java.util.Objects;

public record PinMessageResult(Message message) {
    public PinMessageResult {
        Objects.requireNonNull(message, "message must not be null");
    }
}
