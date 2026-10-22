package com.example.discord.guild;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.EffectivePermissionCalculator;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionOverwrite;
import com.example.discord.permission.PermissionSet;
import com.example.discord.permission.RolePermission;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class GuildService {
    private final GuildStore store;
    private final EffectivePermissionCalculator calculator = new EffectivePermissionCalculator();

    public GuildService(GuildStore store) {
        this.store = java.util.Objects.requireNonNull(store, "store must not be null");
    }

    public Guild createGuild(String name, UUID ownerId) {
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
        store.create(guild);
        return guild;
    }

    public Channel createChannel(UUID guildId, String name, ChannelType type, UUID parentId) {
        return store.update(guildId, guild -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("channel name is required");
            }
            Channel channel = new Channel(UUID.randomUUID(), guild.id(), name, type, parentId, List.of());
            guild.putChannel(channel);
            return channel;
        });
    }

    public Role createRole(UUID guildId, String name) {
        return createRole(guildId, name, PermissionSet.empty());
    }

    public List<Role> roles(UUID guildId) {
        return guild(guildId).roles();
    }

    public Role createRole(UUID guildId, String name, PermissionSet permissions) {
        return store.update(guildId, guild -> {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("role name is required");
            }
            if (permissions == null) {
                throw new IllegalArgumentException("permissions are required");
            }
            Role role = new Role(UUID.randomUUID(), name, permissions);
            guild.putRole(role);
            return role;
        });
    }

    public Role assignRolePermissions(UUID guildId, UUID roleId, PermissionSet permissions) {
        return store.update(guildId, guild -> {
            if (permissions == null) {
                throw new IllegalArgumentException("permissions are required");
            }
            Role role = guild.role(roleId).withPermissions(permissions);
            guild.putRole(role);
            return role;
        });
    }

    public GuildMember assignRoleToMember(UUID guildId, UUID memberId, UUID roleId) {
        return store.update(guildId, guild -> {
            guild.role(roleId);
            GuildMember member = guild.member(memberId).withRole(roleId);
            guild.putMember(member);
            return member;
        });
    }

    public GuildMember addMember(UUID guildId, UUID memberId) {
        return store.update(guildId, guild -> {
            if (memberId == null) {
                throw new IllegalArgumentException("memberId is required");
            }
            try {
                return guild.member(memberId);
            } catch (IllegalArgumentException ignored) {
                GuildMember member = new GuildMember(memberId, java.util.Set.of(guild.everyoneRole().id()));
                guild.putMember(member);
                return member;
            }
        });
    }

    public Channel addChannelRoleOverwrite(
        UUID guildId,
        UUID channelId,
        UUID roleId,
        PermissionSet allow,
        PermissionSet deny
    ) {
        return store.update(guildId, guild -> {
            if (allow == null || deny == null) {
                throw new IllegalArgumentException("overwrite permissions are required");
            }
            guild.role(roleId);
            Channel channel = guild.channel(channelId);
            Channel updated = channel.withOverwrite(new PermissionOverwrite(roleId, allow, deny));
            guild.putChannel(updated);
            return updated;
        });
    }

    public List<Channel> visibleChannels(UUID guildId, UUID memberId) {
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

    public List<UUID> guildIdsForMember(UUID memberId) {
        return memberId == null ? List.of() : store.guildIdsForMember(memberId);
    }

    public boolean isGuildMemberOrOwner(UUID guildId, UUID memberId) {
        if (memberId == null) {
            return false;
        }
        return isGuildMemberOrOwner(guild(guildId), memberId);
    }

    public boolean canManageRoles(UUID guildId, UUID requesterId) {
        return canManage(guild(guildId), requesterId, Permission.MANAGE_ROLES);
    }

    public boolean canGrantRolePermissions(UUID guildId, UUID requesterId, PermissionSet requestedPermissions) {
        if (requestedPermissions == null) {
            return false;
        }
        return canGrantRolePermissions(guild(guildId), requesterId, requestedPermissions);
    }

    private boolean canGrantRolePermissions(Guild guild, UUID requesterId, PermissionSet requestedPermissions) {
        if (guild.ownerId().equals(requesterId)) {
            return true;
        }
        if ((requestedPermissions.raw() & Permission.ADMINISTRATOR.bit()) != 0) {
            return false;
        }
        PermissionSet requesterPermissions = guildPermissions(guild, requesterId);
        return (requestedPermissions.raw() & ~requesterPermissions.raw()) == 0;
    }

    public boolean canAssignRole(UUID guildId, UUID requesterId, UUID roleId) {
        Guild guild = guild(guildId);
        Role role = guild.role(roleId);
        if (guild.ownerId().equals(requesterId)) {
            return true;
        }
        return canGrantRolePermissions(guild, requesterId, role.permissions());
    }

    public boolean canAssignRoles(UUID guildId, UUID requesterId, List<UUID> roleIds) {
        Guild guild = guild(guildId);
        List<UUID> requestedRoleIds = roleIds == null ? List.of() : roleIds;
        for (UUID roleId : requestedRoleIds) {
            Role role = guild.role(roleId);
            if (!guild.ownerId().equals(requesterId) && !canGrantRolePermissions(guild, requesterId, role.permissions())) {
                return false;
            }
        }
        return true;
    }

    public void requireChannel(UUID guildId, UUID channelId) {
        guild(guildId).channel(channelId);
    }

    public Channel channel(UUID guildId, UUID channelId) {
        return guild(guildId).channel(channelId);
    }

    public void requireRoles(UUID guildId, List<UUID> roleIds) {
        Guild guild = guild(guildId);
        List<UUID> requestedRoleIds = roleIds == null ? List.of() : roleIds;
        for (UUID roleId : requestedRoleIds) {
            guild.role(roleId);
        }
    }

    public boolean canManageChannels(UUID guildId, UUID requesterId) {
        return canManage(guild(guildId), requesterId, Permission.MANAGE_CHANNELS);
    }

    public boolean canViewChannel(UUID guildId, UUID channelId, UUID memberId) {
        return canUseChannelPermission(guildId, channelId, memberId, Permission.VIEW_CHANNEL);
    }

    /** Filters fan-out candidates against one guild snapshot instead of re-reading the guild per recipient. */
    public Set<UUID> channelViewers(Guild guild, UUID channelId, Collection<UUID> candidateIds) {
        java.util.Objects.requireNonNull(guild, "guild must not be null");
        Set<UUID> viewers = new LinkedHashSet<>();
        for (UUID candidateId : candidateIds) {
            if (canUseChannelPermission(guild, channelId, candidateId, Permission.VIEW_CHANNEL)) {
                viewers.add(candidateId);
            }
        }
        return viewers;
    }

    public boolean canSendMessages(UUID guildId, UUID channelId, UUID memberId) {
        return canUseChannelPermission(guildId, channelId, memberId, Permission.VIEW_CHANNEL, Permission.SEND_MESSAGES);
    }

    public boolean canManageMessages(UUID guildId, UUID channelId, UUID memberId) {
        return canUseChannelPermission(guildId, channelId, memberId, Permission.VIEW_CHANNEL, Permission.MANAGE_MESSAGES);
    }

    public boolean canManageExpressions(UUID guildId, UUID requesterId) {
        return canManage(guild(guildId), requesterId, Permission.MANAGE_EXPRESSIONS);
    }

    public UUID guildIdForChannel(UUID channelId) {
        if (channelId == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        return store.guildIdForChannel(channelId);
    }

    public boolean channelBelongsToGuild(UUID guildId, UUID channelId) {
        try {
            guild(guildId).channel(channelId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
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

    private boolean isGuildMemberOrOwner(Guild guild, UUID memberId) {
        if (guild.ownerId().equals(memberId)) {
            return true;
        }
        try {
            guild.member(memberId);
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean canUseChannelPermission(UUID guildId, UUID channelId, UUID memberId, Permission... requiredPermissions) {
        if (memberId == null) {
            return false;
        }
        return canUseChannelPermission(guild(guildId), channelId, memberId, requiredPermissions);
    }

    private boolean canUseChannelPermission(Guild guild, UUID channelId, UUID memberId, Permission... requiredPermissions) {
        if (memberId == null) {
            return false;
        }
        if (guild.ownerId().equals(memberId)) {
            return true;
        }
        try {
            PermissionSet permissions = channelPermissions(guild, channelId, memberId);
            for (Permission permission : requiredPermissions) {
                if (!permissions.allows(permission)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private PermissionSet channelPermissions(Guild guild, UUID channelId, UUID memberId) {
        Channel channel = guild.channel(channelId);
        GuildMember member = guild.member(memberId);
        PermissionSet everyonePermissions = guild.everyoneRole().permissions();

        List<RolePermission> rolePermissions = member.roleIds().stream()
            .filter(roleId -> !roleId.equals(guild.everyoneRole().id()))
            .map(roleId -> new RolePermission(roleId, guild.role(roleId).permissions()))
            .toList();
        List<PermissionOverwrite> relevantOverwrites = channel.overwrites().stream()
            .filter(overwrite -> member.roleIds().contains(overwrite.roleId()))
            .toList();

        return calculator.calculate(
            everyonePermissions,
            rolePermissions,
            relevantOverwrites,
            guild.everyoneRole().id()
        );
    }

    private PermissionSet guildPermissions(Guild guild, UUID userId) {
        GuildMember member = guild.member(userId);
        PermissionSet permissions = PermissionSet.empty();
        for (UUID roleId : member.roleIds()) {
            permissions = permissions.grantAll(guild.role(roleId).permissions());
        }
        return permissions;
    }

    public Guild guild(UUID guildId) {
        return store.get(guildId);
    }
}
