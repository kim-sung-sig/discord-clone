package com.example.discord.guild;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.PermissionOverwrite;
import com.example.discord.permission.PermissionSet;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class JdbcGuildSnapshotStore implements GuildSnapshotStore {
    private final DataSource dataSource;

    JdbcGuildSnapshotStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Guild> loadAll() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, name, owner_id
                 FROM guilds
                 ORDER BY created_at, name
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            List<Guild> guilds = new ArrayList<>();
            while (resultSet.next()) {
                UUID guildId = resultSet.getObject("id", UUID.class);
                Map<UUID, Role> roles = loadRoles(connection, guildId);
                UUID everyoneRoleId = roles.values().stream()
                    .filter(role -> "@everyone".equals(role.name()))
                    .map(Role::id)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("guild missing @everyone role"));
                guilds.add(new Guild(
                    guildId,
                    resultSet.getString("name"),
                    resultSet.getObject("owner_id", UUID.class),
                    everyoneRoleId,
                    loadMembers(connection, guildId),
                    roles,
                    loadChannels(connection, guildId)
                ));
            }
            return List.copyOf(guilds);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load guild snapshots", exception);
        }
    }

    @Override
    public void save(Guild guild) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertGuild(connection, guild);
                replaceGuildChildren(connection, guild);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save guild snapshot", exception);
        }
    }

    private static void upsertGuild(Connection connection, Guild guild) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO guilds(id, name, owner_id)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO UPDATE
            SET name = EXCLUDED.name,
                owner_id = EXCLUDED.owner_id,
                updated_at = now()
            """)) {
            statement.setObject(1, guild.id());
            statement.setString(2, guild.name());
            statement.setObject(3, guild.ownerId());
            statement.executeUpdate();
        }
    }

    private static void replaceGuildChildren(Connection connection, Guild guild) throws SQLException {
        executeUpdate(connection, "DELETE FROM channel_role_overwrites WHERE channel_id IN (SELECT id FROM channels WHERE guild_id = ?)", guild.id());
        executeUpdate(connection, "DELETE FROM guild_member_roles WHERE guild_id = ?", guild.id());
        executeUpdate(connection, "DELETE FROM channels WHERE guild_id = ?", guild.id());
        executeUpdate(connection, "DELETE FROM guild_roles WHERE guild_id = ?", guild.id());
        executeUpdate(connection, "DELETE FROM guild_members WHERE guild_id = ?", guild.id());

        insertRoles(connection, guild);
        insertMembers(connection, guild);
        insertMemberRoles(connection, guild);
        insertChannels(connection, guild);
        insertChannelOverwrites(connection, guild);
    }

    private static Map<UUID, Role> loadRoles(Connection connection, UUID guildId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT id, name, permissions
            FROM guild_roles
            WHERE guild_id = ?
            ORDER BY position, created_at, name
            """)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<UUID, Role> roles = new LinkedHashMap<>();
                while (resultSet.next()) {
                    Role role = new Role(
                        resultSet.getObject("id", UUID.class),
                        resultSet.getString("name"),
                        new PermissionSet(resultSet.getLong("permissions"))
                    );
                    roles.put(role.id(), role);
                }
                return roles;
            }
        }
    }

    private static Map<UUID, GuildMember> loadMembers(Connection connection, UUID guildId) throws SQLException {
        Map<UUID, LinkedHashSet<UUID>> roleIdsByMember = new LinkedHashMap<>();
        try (var statement = connection.prepareStatement("""
            SELECT user_id
            FROM guild_members
            WHERE guild_id = ?
            ORDER BY joined_at, user_id
            """)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    roleIdsByMember.put(resultSet.getObject("user_id", UUID.class), new LinkedHashSet<>());
                }
            }
        }
        try (var statement = connection.prepareStatement("""
            SELECT user_id, role_id
            FROM guild_member_roles
            WHERE guild_id = ?
            ORDER BY assigned_at, role_id
            """)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    UUID memberId = resultSet.getObject("user_id", UUID.class);
                    roleIdsByMember.computeIfAbsent(memberId, ignored -> new LinkedHashSet<>())
                        .add(resultSet.getObject("role_id", UUID.class));
                }
            }
        }
        Map<UUID, GuildMember> members = new LinkedHashMap<>();
        roleIdsByMember.forEach((memberId, roleIds) -> members.put(memberId, new GuildMember(memberId, roleIds)));
        return members;
    }

    private static Map<UUID, Channel> loadChannels(Connection connection, UUID guildId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT id, name, type, parent_id
            FROM channels
            WHERE guild_id = ?
            ORDER BY position, created_at, name
            """)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                Map<UUID, Channel> channels = new LinkedHashMap<>();
                while (resultSet.next()) {
                    UUID channelId = resultSet.getObject("id", UUID.class);
                    Channel channel = new Channel(
                        channelId,
                        guildId,
                        resultSet.getString("name"),
                        ChannelType.valueOf(resultSet.getString("type")),
                        resultSet.getObject("parent_id", UUID.class),
                        loadOverwrites(connection, channelId)
                    );
                    channels.put(channel.id(), channel);
                }
                return channels;
            }
        }
    }

    private static List<PermissionOverwrite> loadOverwrites(Connection connection, UUID channelId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT role_id, allow, deny
            FROM channel_role_overwrites
            WHERE channel_id = ?
            ORDER BY role_id
            """)) {
            statement.setObject(1, channelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<PermissionOverwrite> overwrites = new ArrayList<>();
                while (resultSet.next()) {
                    overwrites.add(new PermissionOverwrite(
                        resultSet.getObject("role_id", UUID.class),
                        new PermissionSet(resultSet.getLong("allow")),
                        new PermissionSet(resultSet.getLong("deny"))
                    ));
                }
                return overwrites;
            }
        }
    }

    private static void insertRoles(Connection connection, Guild guild) throws SQLException {
        int position = 0;
        for (Role role : guild.roles()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO guild_roles(id, guild_id, name, permissions, position)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                statement.setObject(1, role.id());
                statement.setObject(2, guild.id());
                statement.setString(3, role.name());
                statement.setLong(4, role.permissions().raw());
                statement.setInt(5, position++);
                statement.executeUpdate();
            }
        }
    }

    private static void insertMembers(Connection connection, Guild guild) throws SQLException {
        for (GuildMember member : guild.members()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO guild_members(guild_id, user_id)
                VALUES (?, ?)
                """)) {
                statement.setObject(1, guild.id());
                statement.setObject(2, member.userId());
                statement.executeUpdate();
            }
        }
    }

    private static void insertMemberRoles(Connection connection, Guild guild) throws SQLException {
        for (GuildMember member : guild.members()) {
            for (UUID roleId : member.roleIds()) {
                try (var statement = connection.prepareStatement("""
                    INSERT INTO guild_member_roles(guild_id, user_id, role_id)
                    VALUES (?, ?, ?)
                    """)) {
                    statement.setObject(1, guild.id());
                    statement.setObject(2, member.userId());
                    statement.setObject(3, roleId);
                    statement.executeUpdate();
                }
            }
        }
    }

    private static void insertChannels(Connection connection, Guild guild) throws SQLException {
        int position = 0;
        for (Channel channel : guild.channels()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO channels(id, guild_id, parent_id, name, type, position)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
                statement.setObject(1, channel.id());
                statement.setObject(2, guild.id());
                statement.setObject(3, channel.parentId());
                statement.setString(4, channel.name());
                statement.setString(5, channel.type().name());
                statement.setInt(6, position++);
                statement.executeUpdate();
            }
        }
    }

    private static void insertChannelOverwrites(Connection connection, Guild guild) throws SQLException {
        for (Channel channel : guild.channels()) {
            for (PermissionOverwrite overwrite : channel.overwrites()) {
                try (var statement = connection.prepareStatement("""
                    INSERT INTO channel_role_overwrites(channel_id, role_id, allow, deny)
                    VALUES (?, ?, ?, ?)
                    """)) {
                    statement.setObject(1, channel.id());
                    statement.setObject(2, overwrite.roleId());
                    statement.setLong(3, overwrite.allow().raw());
                    statement.setLong(4, overwrite.deny().raw());
                    statement.executeUpdate();
                }
            }
        }
    }

    private static void executeUpdate(Connection connection, String sql, UUID guildId) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, guildId);
            statement.executeUpdate();
        }
    }
}
