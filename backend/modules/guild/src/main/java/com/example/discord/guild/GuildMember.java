package com.example.discord.guild;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record GuildMember(UUID userId, Set<UUID> roleIds) {
    public GuildMember {
        Objects.requireNonNull(userId, "userId must not be null");
        roleIds = Set.copyOf(Objects.requireNonNull(roleIds, "roleIds must not be null"));
    }

    GuildMember withRole(UUID roleId) {
        LinkedHashSet<UUID> updatedRoleIds = new LinkedHashSet<>(roleIds);
        updatedRoleIds.add(roleId);
        return new GuildMember(userId, updatedRoleIds);
    }
}
