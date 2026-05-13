package com.example.discord.guild;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.PermissionOverwrite;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Channel(
    UUID id,
    UUID guildId,
    String name,
    ChannelType type,
    UUID parentId,
    List<PermissionOverwrite> overwrites
) {
    public Channel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        overwrites = List.copyOf(Objects.requireNonNull(overwrites, "overwrites must not be null"));
    }

    Channel withOverwrite(PermissionOverwrite overwrite) {
        List<PermissionOverwrite> updated = overwrites.stream()
            .filter(existing -> !existing.roleId().equals(overwrite.roleId()))
            .toList();
        java.util.ArrayList<PermissionOverwrite> merged = new java.util.ArrayList<>(updated);
        merged.add(overwrite);
        return new Channel(id, guildId, name, type, parentId, merged);
    }
}
