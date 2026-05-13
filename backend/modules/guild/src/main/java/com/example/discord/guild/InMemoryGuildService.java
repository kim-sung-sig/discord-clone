package com.example.discord.guild;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.EffectivePermissionCalculator;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionOverwrite;
import com.example.discord.permission.PermissionSet;
import com.example.discord.permission.RolePermission;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class InMemoryGuildService {
    private final Map<UUID, Guild> guilds = new LinkedHashMap<>();
    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator();

    public synchronized Guild createGuild(String name, UUID ownerId) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("guild name is required");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId is required");
        }

        UUID guildId = UUID.randomUUID();
        UUID everyoneRoleId = UUID.randomUUID();
        Role everyoneRole = new Role(
            everyoneRoleId,
            "@everyone",
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );
        GuildMember ownerMember = new GuildMember(ownerId, java.util.Set.of(everyoneRoleId));

        Map<UUID, GuildMember> members = new LinkedHashMap<>();
        members.put(ownerId, ownerMember);
        Map<UUID, Role> roles = new LinkedHashMap<>();
        roles.put(everyoneRoleId, everyoneRole);

        Guild guild = new Guild(guildId, name, ownerId, everyoneRoleId, members, roles, new LinkedHashMap<>());
        guilds.put(guildId, guild);
        return guild;
    }

    public synchronized Channel createChannel(UUID guildId, String name, ChannelType type, UUID parentId) {
        Guild guild = guild(guildId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("channel name is required");
        }
        Channel channel = new Channel(UUID.randomUUID(), guild.id(), name, type, parentId, List.of());
        guild.putChannel(channel);
        return channel;
    }

    public synchronized Role createRole(UUID guildId, String name) {
        Guild guild = guild(guildId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("role name is required");
        }
        Role role = new Role(UUID.randomUUID(), name, PermissionSet.empty());
        guild.putRole(role);
        return role;
    }

    public synchronized void assignRolePermissions(UUID guildId, UUID roleId, PermissionSet permissions) {
        Guild guild = guild(guildId);
        guild.putRole(guild.role(roleId).withPermissions(permissions));
    }

    public synchronized void assignRoleToMember(UUID guildId, UUID memberId, UUID roleId) {
        Guild guild = guild(guildId);
        guild.role(roleId);
        guild.putMember(guild.member(memberId).withRole(roleId));
    }

    public synchronized void addChannelRoleOverwrite(
        UUID guildId,
        UUID channelId,
        UUID roleId,
        PermissionSet allow,
        PermissionSet deny
    ) {
        Guild guild = guild(guildId);
        guild.role(roleId);
        Channel channel = guild.channel(channelId);
        guild.putChannel(channel.withOverwrite(new PermissionOverwrite(roleId, allow, deny)));
    }

    public synchronized List<Channel> visibleChannels(UUID guildId, UUID memberId) {
        Guild guild = guild(guildId);
        GuildMember member = guild.member(memberId);
        PermissionSet everyonePermissions = guild.everyoneRole().permissions();

        List<RolePermission> rolePermissions = member.roleIds().stream()
            .filter(roleId -> !roleId.equals(guild.everyoneRole().id()))
            .map(roleId -> new RolePermission(roleId, guild.role(roleId).permissions()))
            .toList();

        List<Channel> visible = new ArrayList<>();
        for (Channel channel : guild.channels()) {
            List<PermissionOverwrite> relevantOverwrites = channel.overwrites().stream()
                .filter(overwrite -> member.roleIds().contains(overwrite.roleId()))
                .toList();
            PermissionSet effective = calculator.calculate(
                everyonePermissions,
                rolePermissions,
                relevantOverwrites,
                guild.everyoneRole().id()
            );
            if (effective.allows(Permission.VIEW_CHANNEL)) {
                visible.add(channel);
            }
        }
        return List.copyOf(visible);
    }

    public synchronized Guild guild(UUID guildId) {
        Guild guild = guilds.get(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("guild not found");
        }
        return guild;
    }
}
