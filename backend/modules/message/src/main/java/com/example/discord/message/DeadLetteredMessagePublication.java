package com.example.discord.message;

import java.time.Instant;
import java.util.Objects;

public record DeadLetteredMessagePublication(
    MessagePublished event,
    int attempts,
    String lastError,
    Instant deadLetteredAt
) {
    public DeadLetteredMessagePublication {
        Objects.requireNonNull(event, "event must not be null");
        if (attempts < 1) {
            throw new IllegalArgumentException("attempts must be positive");
        }
        lastError = lastError == null ? "" : lastError;
        Objects.requireNonNull(deadLetteredAt, "deadLetteredAt must not be null");
    }
}
