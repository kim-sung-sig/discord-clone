package com.example.discord.social;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record GroupDmChannel(
    UUID id,
    String name,
    UUID ownerId,
    Set<UUID> members,
    GroupCallState callState,
    Instant createdAt,
    Instant updatedAt
) {
    public GroupDmChannel {
        if (!members.contains(ownerId)) {
            throw new IllegalArgumentException("group owner must be a member");
        }
        members = Set.copyOf(members);
    }
}
