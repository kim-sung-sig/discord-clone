package com.example.discord.expression;

import java.util.Set;
import java.util.UUID;

public record ReactionSummary(String emojiKey, int count, Set<UUID> userIds) {
    public ReactionSummary {
        userIds = Set.copyOf(userIds);
    }

    public boolean reactedBy(UUID userId) {
        return userIds.contains(userId);
    }
}
