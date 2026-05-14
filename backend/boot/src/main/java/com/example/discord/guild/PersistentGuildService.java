package com.example.discord.guild;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.PermissionSet;
import java.util.UUID;

class PersistentGuildService extends InMemoryGuildService {
    private final GuildSnapshotStore snapshots;

    PersistentGuildService(GuildSnapshotStore snapshots) {
        this.snapshots = snapshots;
        snapshots.loadAll().forEach(this::putGuild);
    }

    @Override
    public synchronized Guild createGuild(String name, UUID ownerId) {
        Guild guild = super.createGuild(name, ownerId);
        snapshots.save(guild);
        return guild;
    }

    @Override
    public synchronized Channel createChannel(UUID guildId, String name, ChannelType type, UUID parentId) {
        Channel channel = super.createChannel(guildId, name, type, parentId);
        snapshots.save(guild(guildId));
        return channel;
    }

    @Override
    public synchronized Role createRole(UUID guildId, String name, PermissionSet permissions) {
        Role role = super.createRole(guildId, name, permissions);
        snapshots.save(guild(guildId));
        return role;
    }

    @Override
    public synchronized Role assignRolePermissions(UUID guildId, UUID roleId, PermissionSet permissions) {
        Role role = super.assignRolePermissions(guildId, roleId, permissions);
        snapshots.save(guild(guildId));
        return role;
    }

    @Override
    public synchronized GuildMember assignRoleToMember(UUID guildId, UUID memberId, UUID roleId) {
        GuildMember member = super.assignRoleToMember(guildId, memberId, roleId);
        snapshots.save(guild(guildId));
        return member;
    }

    @Override
    public synchronized GuildMember addMember(UUID guildId, UUID memberId) {
        GuildMember member = super.addMember(guildId, memberId);
        snapshots.save(guild(guildId));
        return member;
    }

    @Override
    public synchronized Channel addChannelRoleOverwrite(
        UUID guildId,
        UUID channelId,
        UUID roleId,
        PermissionSet allow,
        PermissionSet deny
    ) {
        Channel channel = super.addChannelRoleOverwrite(guildId, channelId, roleId, allow, deny);
        snapshots.save(guild(guildId));
        return channel;
    }
}
