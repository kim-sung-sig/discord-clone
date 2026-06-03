package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record UserMentionTarget(UUID userId) implements MessageMentionTarget {
    public UserMentionTarget {
        Objects.requireNonNull(userId, "userId must not be null");
    }
}
