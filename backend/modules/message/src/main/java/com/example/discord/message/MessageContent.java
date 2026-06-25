package com.example.discord.message;

import java.util.Objects;

public record MessageContent(String value) {
    public MessageContent {
        Objects.requireNonNull(value, "value must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("message content must not be blank");
        }
        if (normalized.length() > 2000) {
            throw new IllegalArgumentException("message content must not exceed 2000 characters");
        }
        value = normalized;
    }
}
