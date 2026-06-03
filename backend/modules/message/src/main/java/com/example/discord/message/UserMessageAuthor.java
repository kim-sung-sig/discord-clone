package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record UserMessageAuthor(UUID userId) implements MessageAuthor {
    public UserMessageAuthor {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
