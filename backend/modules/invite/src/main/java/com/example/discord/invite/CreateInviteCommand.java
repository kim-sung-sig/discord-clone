package com.example.discord.invite;

import java.util.List;
import java.util.UUID;

public record CreateInviteCommand(
    UUID guildId,
    UUID channelId,
    UUID creatorId,
    long maxAgeSeconds,
    int maxUses,
    boolean temporary,
    List<UUID> roleGrantIds
) {
    public CreateInviteCommand {
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        if (creatorId == null) {
            throw new IllegalArgumentException("creatorId is required");
        }
        if (maxUses < 0) {
            throw new IllegalArgumentException("maxUses must not be negative");
        }
        roleGrantIds = roleGrantIds == null ? List.of() : List.copyOf(roleGrantIds);
    }
}
