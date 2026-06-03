package com.example.discord.message;

import java.time.Instant;
import java.util.Objects;

public record MessageEdit(MessageContent content, Instant editedAt) {
    public MessageEdit {
        Objects.requireNonNull(content, "content must not be null");
        Objects.requireNonNull(editedAt, "editedAt must not be null");
    }
}
