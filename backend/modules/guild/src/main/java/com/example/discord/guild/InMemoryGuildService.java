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
        return createRole(guildId, name, PermissionSet.empty());
    }

    public synchronized List<Role> roles(UUID guildId) {
        return guild(guildId).roles();
    }

    public synchronized Role createRole(UUID guildId, String name, PermissionSet permissions) {
        Guild guild = guild(guildId);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("role name is required");
        }
        if (permissions == null) {
            throw new IllegalArgumentException("permissions are required");
        }
        Role role = new Role(UUID.randomUUID(), name, permissions);
        guild.putRole(role);
        return role;
    }

    public synchronized Role assignRolePermissions(UUID guildId, UUID roleId, PermissionSet permissions) {
        if (permissions == null) {
            throw new IllegalArgumentException("permissions are required");
        }
        Guild guild = guild(guildId);
        Role role = guild.role(roleId).withPermissions(permissions);
        guild.putRole(role);
        return role;
    }

    public synchronized GuildMember assignRoleToMember(UUID guildId, UUID memberId, UUID roleId) {
        Guild guild = guild(guildId);
        guild.role(roleId);
        GuildMember member = guild.member(memberId).withRole(roleId);
        guild.putMember(member);
        return member;
    }

    public synchronized GuildMember addMember(UUID guildId, UUID memberId) {
        if (memberId == null) {
            throw new IllegalArgumentException("memberId is required");
        }
        Guild guild = guild(guildId);
        try {
            return guild.member(memberId);
        } catch (IllegalArgumentException ignored) {
            GuildMember member = new GuildMember(memberId, java.util.Set.of(guild.everyoneRole().id()));
            guild.putMember(member);
            return member;
        }
    }

    public synchronized Channel addChannelRoleOverwrite(
        UUID guildId,
        UUID channelId,
        UUID roleId,
        PermissionSet allow,
        PermissionSet deny
    ) {
        if (allow == null || deny == null) {
            throw new IllegalArgumentException("overwrite permissions are required");
        }
        Guild guild = guild(guildId);
        guild.role(roleId);
        Channel channel = guild.channel(channelId);
        Channel updated = channel.withOverwrite(new PermissionOverwrite(roleId, allow, deny));
        guild.putChannel(updated);
        return updated;
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

    public synchronized boolean canManageRoles(UUID guildId, UUID requesterId) {
        return canManage(guild(guildId), requesterId, Permission.MANAGE_ROLES);
    }

    public synchronized boolean canGrantRolePermissions(UUID guildId, UUID requesterId, PermissionSet requestedPermissions) {
        if (requestedPermissions == null) {
            return false;
        }
        Guild guild = guild(guildId);
        if (guild.ownerId().equals(requesterId)) {
            return true;
        }
        if ((requestedPermissions.raw() & Permission.ADMINISTRATOR.bit()) != 0) {
            return false;
        }
        PermissionSet requesterPermissions = guildPermissions(guild, requesterId);
        return (requestedPermissions.raw() & ~requesterPermissions.raw()) == 0;
    }

    public synchronized boolean canAssignRole(UUID guildId, UUID requesterId, UUID roleId) {
        Guild guild = guild(guildId);
        Role role = guild.role(roleId);
        if (guild.ownerId().equals(requesterId)) {
            return true;
        }
        return canGrantRolePermissions(guildId, requesterId, role.permissions());
    }

    public synchronized boolean canAssignRoles(UUID guildId, UUID requesterId, List<UUID> roleIds) {
        Guild guild = guild(guildId);
        List<UUID> requestedRoleIds = roleIds == null ? List.of() : roleIds;
        for (UUID roleId : requestedRoleIds) {
            Role role = guild.role(roleId);
            if (!guild.ownerId().equals(requesterId) && !canGrantRolePermissions(guildId, requesterId, role.permissions())) {
                return false;
            }
        }
        return true;
    }

    public synchronized void requireChannel(UUID guildId, UUID channelId) {
        guild(guildId).channel(channelId);
    }

    public synchronized void requireRoles(UUID guildId, List<UUID> roleIds) {
        Guild guild = guild(guildId);
        List<UUID> requestedRoleIds = roleIds == null ? List.of() : roleIds;
        for (UUID roleId : requestedRoleIds) {
            guild.role(roleId);
        }
    }

    public synchronized boolean canManageChannels(UUID guildId, UUID requesterId) {
        return canManage(guild(guildId), requesterId, Permission.MANAGE_CHANNELS);
    }

    private boolean canManage(Guild guild, UUID requesterId, Permission permission) {
        if (requesterId == null) {
            return false;
        }
        if (guild.ownerId().equals(requesterId)) {
            return true;
        }
        try {
            return guildPermissions(guild, requesterId).allows(permission);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private PermissionSet guildPermissions(Guild guild, UUID userId) {
        GuildMember member = guild.member(userId);
        PermissionSet permissions = PermissionSet.empty();
        for (UUID roleId : member.roleIds()) {
            permissions = permissions.grantAll(guild.role(roleId).permissions());
        }
        return permissions;
    }

    public synchronized Guild guild(UUID guildId) {
        Guild guild = guilds.get(guildId);
        if (guild == null) {
            throw new IllegalArgumentException("guild not found");
        }
        return guild;
    }
}
