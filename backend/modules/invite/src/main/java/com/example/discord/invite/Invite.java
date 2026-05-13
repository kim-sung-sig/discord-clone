package com.example.discord.invite;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record Invite(
    String code,
    UUID guildId,
    UUID channelId,
    UUID creatorId,
    long maxAgeSeconds,
    int maxUses,
    boolean temporary,
    List<UUID> roleGrantIds,
    Instant createdAt,
    Instant deletedAt,
    Set<UUID> acceptedMemberIds
) {
    public Invite {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code is required");
        }
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        if (creatorId == null) {
            throw new IllegalArgumentException("creatorId is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
        roleGrantIds = roleGrantIds == null ? List.of() : List.copyOf(roleGrantIds);
        acceptedMemberIds = acceptedMemberIds == null ? Set.of() : Set.copyOf(acceptedMemberIds);
    }

    public int uses() {
        return acceptedMemberIds.size();
    }

    public Instant expiresAt() {
        if (maxAgeSeconds == 0) {
            return null;
        }
        return createdAt.plusSeconds(maxAgeSeconds);
    }
}
