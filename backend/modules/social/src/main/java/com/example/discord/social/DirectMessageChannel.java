package com.example.discord.social;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record DirectMessageChannel(
    UUID id,
    UUID firstUserId,
    UUID secondUserId,
    Instant createdAt
) {
    public boolean contains(UUID userId) {
        return firstUserId.equals(userId) || secondUserId.equals(userId);
    }

    public Set<UUID> participants() {
        return Set.of(firstUserId, secondUserId);
    }
}
