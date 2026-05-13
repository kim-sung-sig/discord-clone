package com.example.discord.common;

import java.util.UUID;

public record Identifier(UUID value) {
    public static Identifier from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("identifier must not be blank");
        }
        return new Identifier(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
