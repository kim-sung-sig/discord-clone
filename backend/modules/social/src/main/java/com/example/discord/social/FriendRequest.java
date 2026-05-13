package com.example.discord.social;

import java.time.Instant;
import java.util.UUID;

public record FriendRequest(
    UUID id,
    UUID requesterId,
    UUID addresseeId,
    FriendshipStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
