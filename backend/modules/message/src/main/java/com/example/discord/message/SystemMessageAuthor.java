package com.example.discord.message;

import java.util.Objects;

public record SystemMessageAuthor(String reason) implements MessageAuthor {
    public SystemMessageAuthor {
        Objects.requireNonNull(reason, "reason must not be null");
        String normalized = reason.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("system author reason must not be blank");
        }
        reason = normalized;
    }
}
