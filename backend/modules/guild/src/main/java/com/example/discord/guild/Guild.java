package com.example.discord.guild;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Guild {
    private final UUID id;
    private final String name;
    private final UUID ownerId;
    private final UUID everyoneRoleId;
    private final Map<UUID, GuildMember> members;
    private final Map<UUID, Role> roles;
    private final Map<UUID, Channel> channels;

    Guild(
        UUID id,
        String name,
        UUID ownerId,
        UUID everyoneRoleId,
        Map<UUID, GuildMember> members,
        Map<UUID, Role> roles,
        Map<UUID, Channel> channels
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.ownerId = Objects.requireNonNull(ownerId, "ownerId must not be null");
        this.everyoneRoleId = Objects.requireNonNull(everyoneRoleId, "everyoneRoleId must not be null");
        this.members = Objects.requireNonNull(members, "members must not be null");
        this.roles = Objects.requireNonNull(roles, "roles must not be null");
        this.channels = Objects.requireNonNull(channels, "channels must not be null");
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public List<GuildMember> members() {
        return List.copyOf(members.values());
    }

    public List<Role> roles() {
        return List.copyOf(roles.values());
    }

    public List<Channel> channels() {
        return List.copyOf(channels.values());
    }

    public Role everyoneRole() {
        return roles.get(everyoneRoleId);
    }

    public GuildMember member(UUID userId) {
        GuildMember member = members.get(userId);
        if (member == null) {
            throw new IllegalArgumentException("member not found");
        }
        return member;
    }

    Role role(UUID roleId) {
        Role role = roles.get(roleId);
        if (role == null) {
            throw new IllegalArgumentException("role not found");
        }
        return role;
    }

    Channel channel(UUID channelId) {
        Channel channel = channels.get(channelId);
        if (channel == null) {
            throw new IllegalArgumentException("channel not found");
        }
        return channel;
    }

    void putMember(GuildMember member) {
        members.put(member.userId(), member);
    }

    void putRole(Role role) {
        roles.put(role.id(), role);
    }

    void putChannel(Channel channel) {
        channels.put(channel.id(), channel);
    }
}
