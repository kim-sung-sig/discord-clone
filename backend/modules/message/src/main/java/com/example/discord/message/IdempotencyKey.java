package com.example.discord.message;

import java.util.Objects;

public record IdempotencyKey(String value) {
    public IdempotencyKey {
        Objects.requireNonNull(value, "value must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("idempotency key must not be blank");
        }
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("idempotency key must not exceed 128 characters");
        }
        value = normalized;
    }
}
