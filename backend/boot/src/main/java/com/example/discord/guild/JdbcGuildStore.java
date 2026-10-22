package com.example.discord.guild;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.AuthorizationAudience;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.EffectivePermissionCalculator;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionOverwrite;
import com.example.discord.permission.PermissionSet;
import com.example.discord.permission.RolePermission;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcGuildStore implements GuildStore {
    private static final Logger log = LoggerFactory.getLogger(JdbcGuildStore.class);
    private final DataSource dataSource;

    JdbcGuildStore(DataSource dataSource) {
        this.dataSource = java.util.Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Guild get(UUID guildId) {
        return readSnapshot(connection -> loadGuild(connection, guildId, false));
    }

    @Override
    public void create(Guild guild) {
        transact(connection -> {
            upsertGuild(connection, guild);
            persistChanges(connection, guild, Map.of(), Map.of(), Map.of());
            publishAuthorization(connection, guild);
            return null;
        });
        log.info("Guild stored guildId={} operation=create status=committed", guild.id());
    }

    @Override
    public <T> T update(UUID guildId, Function<Guild, T> mutation) {
        T result = transact(connection -> {
            Guild guild = loadGuild(connection, guildId, true);
            Map<UUID, Role> roles = guild.roles().stream().collect(Collectors.toMap(Role::id, Function.identity()));
            Map<UUID, GuildMember> members = guild.members().stream()
                .collect(Collectors.toMap(GuildMember::userId, Function.identity()));
            Map<UUID, Channel> channels = guild.channels().stream().collect(Collectors.toMap(Channel::id, Function.identity()));
            T changed = mutation.apply(guild);
            persistChanges(connection, guild, roles, members, channels);
            publishAuthorization(connection, guild);
            return changed;
        });
        log.info("Guild stored guildId={} operation=update status=committed", guildId);
        return result;
    }

    @Override
    public List<UUID> guildIdsForMember(UUID memberId) {
        return transact(connection -> {
            try (var statement = connection.prepareStatement(
                "SELECT id FROM guilds WHERE owner_id = ? OR EXISTS "
                    + "(SELECT 1 FROM guild_members WHERE guild_id = guilds.id AND user_id = ?) ORDER BY created_at,id")) {
                statement.setObject(1, memberId);
                statement.setObject(2, memberId);
                try (var rows = statement.executeQuery()) {
                    List<UUID> guilds = new ArrayList<>();
                    while (rows.next()) {
                        guilds.add(rows.getObject(1, UUID.class));
                    }
                    return List.copyOf(guilds);
                }
            }
        });
    }

    @Override
    public UUID guildIdForChannel(UUID channelId) {
        return transact(connection -> {
            try (var statement = connection.prepareStatement("SELECT guild_id FROM channels WHERE id = ?")) {
                statement.setObject(1, channelId);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new IllegalArgumentException("channel not found");
                    }
                    return rows.getObject(1, UUID.class);
                }
            }
        });
    }

    private static Guild loadGuild(Connection connection, UUID guildId, boolean write) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT id,name,owner_id FROM guilds WHERE id = ?" + (write ? " FOR UPDATE" : ""))) {
            statement.setObject(1, guildId);
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalArgumentException("guild not found");
                }
                Map<UUID, Role> roles = loadRoles(connection, guildId);
                UUID everyone = roles.values().stream().filter(role -> "@everyone".equals(role.name()))
                    .map(Role::id).findFirst().orElseThrow(() -> new IllegalStateException("everyone role missing"));
                return new Guild(guildId, rows.getString("name"), rows.getObject("owner_id", UUID.class),
                    everyone, loadMembers(connection, guildId), roles, loadChannels(connection, guildId));
            }
        }
    }

    private static void persistChanges(
        Connection connection, Guild guild, Map<UUID, Role> roles,
        Map<UUID, GuildMember> members, Map<UUID, Channel> channels
    ) throws SQLException {
        insertRoles(connection, guild, roles);
        insertMembers(connection, guild, members);
        insertMemberRoles(connection, guild, members);
        insertChannels(connection, guild, channels);
        insertChannelOverwrites(connection, guild, channels);
    }

    private static void publishAuthorization(Connection connection, Guild guild) throws SQLException {
        long version = nextPermissionVersion(connection, guild.id());
        appendAuthorizationOutbox(connection, guild, version);
        appendAuthorizationWatermarks(connection, guild, version);
    }

    // Reads span several tables; a REPEATABLE READ snapshot keeps them consistent without a guild row lock, so
    // permission reads neither wait for writers' FOR UPDATE nor block them. Read-only snapshots never fail serialization.
    private <T> T readSnapshot(SqlAction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            int isolation = connection.getTransactionIsolation();
            boolean readOnly = connection.isReadOnly();
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            connection.setReadOnly(true);
            try {
                T result = action.apply(connection);
                connection.commit();
                restore(connection, isolation, readOnly);
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                    restore(connection, isolation, readOnly);
                } catch (SQLException cleanupFailure) {
                    exception.addSuppressed(cleanupFailure);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read guild state", exception);
        }
    }

    private static void restore(Connection connection, int isolation, boolean readOnly) throws SQLException {
        connection.setReadOnly(readOnly);
        connection.setTransactionIsolation(isolation);
    }

    private <T> T transact(SqlAction<T> action) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = action.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    exception.addSuppressed(rollbackFailure);
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to persist guild state", exception);
        }
    }

    @FunctionalInterface
    private interface SqlAction<T> {
        T apply(Connection connection) throws SQLException;
    }

    private static long nextPermissionVersion(Connection connection, UUID guildId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO guild_authorization_versions(guild_id, permission_version)
            VALUES (?, 1)
            ON CONFLICT (guild_id) DO UPDATE
            SET permission_version = guild_authorization_versions.permission_version + 1,
                updated_at = now()
            RETURNING permission_version
            """)) {
            statement.setObject(1, guildId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("failed to allocate guild authorization version");
                }
                return resultSet.getLong(1);
            }
        }
    }

    private static void appendAuthorizationOutbox(
        Connection connection,
        Guild guild,
        long permissionVersion
    ) throws SQLException {
        Role everyone = guild.everyoneRole();
        EffectivePermissionCalculator calculator = new EffectivePermissionCalculator();
        UUID correlationId = UUID.randomUUID();
        for (GuildMember member : guild.members()) {
            List<RolePermission> rolePermissions = guild.roles().stream()
                .filter(role -> member.roleIds().contains(role.id()) && !role.id().equals(everyone.id()))
                .map(role -> new RolePermission(role.id(), role.permissions()))
                .toList();
            appendAuthorizationEvent(
                connection,
                guild,
                member.userId(),
                AuthorizationResourceType.GUILD,
                guild.id(),
                calculator.calculate(everyone.permissions(), rolePermissions, List.of(), everyone.id()),
                permissionVersion,
                correlationId
            );
            for (Channel channel : guild.channels()) {
                List<PermissionOverwrite> relevantOverwrites = channel.overwrites().stream()
                    .filter(overwrite -> member.roleIds().contains(overwrite.roleId()))
                    .toList();
                appendAuthorizationEvent(
                    connection,
                    guild,
                    member.userId(),
                    AuthorizationResourceType.CHANNEL,
                    channel.id(),
                    calculator.calculate(everyone.permissions(), rolePermissions, relevantOverwrites, everyone.id()),
                    permissionVersion,
                    correlationId
                );
            }
        }
    }

    private static void appendAuthorizationEvent(
        Connection connection,
        Guild guild,
        UUID subjectId,
        AuthorizationResourceType resourceType,
        UUID resourceId,
        PermissionSet permissions,
        long permissionVersion,
        UUID correlationId
    ) throws SQLException {
        for (AuthorizationAudience audience : AuthorizationAudience.values()) {
            if (!audienceNeedsResource(audience, resourceType)) {
                continue;
            }
            try (var statement = connection.prepareStatement("""
                INSERT INTO authorization_projection_outbox(
                    event_id, guild_id, event_kind, subject_id, resource_type, resource_id,
                    permission_bits, permission_version, audience, correlation_id
                ) VALUES (?, ?, 'PROJECTION_UPDATED', ?, ?, ?, ?, ?, ?, ?)
                """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guild.id());
                statement.setObject(3, subjectId);
                statement.setString(4, resourceType.name());
                statement.setObject(5, resourceId);
                statement.setLong(6, permissions.raw());
                statement.setLong(7, permissionVersion);
                statement.setString(8, audience.name());
                statement.setObject(9, correlationId);
                statement.executeUpdate();
            }
        }
    }

    private static boolean audienceNeedsResource(
        AuthorizationAudience audience,
        AuthorizationResourceType resourceType
    ) {
        return audience == AuthorizationAudience.NOTIFICATION
            ? resourceType == AuthorizationResourceType.GUILD
            : resourceType == AuthorizationResourceType.CHANNEL;
    }

    private static void appendAuthorizationWatermarks(
        Connection connection,
        Guild guild,
        long permissionVersion
    ) throws SQLException {
        for (AuthorizationAudience audience : AuthorizationAudience.values()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO authorization_projection_outbox(
                    event_id, guild_id, event_kind, permission_version, audience
                ) VALUES (?, ?, 'WATERMARK_ADVANCED', ?, ?)
                """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, guild.id());
                statement.setLong(3, permissionVersion);
                statement.setString(4, audience.name());
                statement.executeUpdate();
            }
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

    private static void insertRoles(Connection connection, Guild guild, Map<UUID, Role> previous) throws SQLException {
        int position = 0;
        for (Role role : guild.roles()) {
            int currentPosition = position++;
            if (role.equals(previous.get(role.id()))) {
                continue;
            }
            try (var statement = connection.prepareStatement("""
                INSERT INTO guild_roles(id, guild_id, name, permissions, position)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET name = EXCLUDED.name, permissions = EXCLUDED.permissions, position = EXCLUDED.position
                """)) {
                statement.setObject(1, role.id());
                statement.setObject(2, guild.id());
                statement.setString(3, role.name());
                statement.setLong(4, role.permissions().raw());
                statement.setInt(5, currentPosition);
                statement.executeUpdate();
            }
        }
    }

    private static void insertMembers(Connection connection, Guild guild, Map<UUID, GuildMember> previous) throws SQLException {
        for (GuildMember member : guild.members()) {
            if (member.equals(previous.get(member.userId()))) {
                continue;
            }
            try (var statement = connection.prepareStatement("""
                INSERT INTO guild_members(guild_id, user_id)
                VALUES (?, ?)
                ON CONFLICT (guild_id, user_id) DO NOTHING
                """)) {
                statement.setObject(1, guild.id());
                statement.setObject(2, member.userId());
                statement.executeUpdate();
            }
        }
    }

    private static void insertMemberRoles(Connection connection, Guild guild, Map<UUID, GuildMember> previous) throws SQLException {
        for (GuildMember member : guild.members()) {
            for (UUID roleId : member.roleIds()) {
                GuildMember before = previous.get(member.userId());
                if (before != null && before.roleIds().contains(roleId)) {
                    continue;
                }
                try (var statement = connection.prepareStatement("""
                    INSERT INTO guild_member_roles(guild_id, user_id, role_id)
                    VALUES (?, ?, ?)
                    ON CONFLICT (guild_id, user_id, role_id) DO NOTHING
                    """)) {
                    statement.setObject(1, guild.id());
                    statement.setObject(2, member.userId());
                    statement.setObject(3, roleId);
                    statement.executeUpdate();
                }
            }
        }
    }

    private static void insertChannels(Connection connection, Guild guild, Map<UUID, Channel> previous) throws SQLException {
        int position = 0;
        for (Channel channel : guild.channels()) {
            int currentPosition = position++;
            if (channel.equals(previous.get(channel.id()))) {
                continue;
            }
            try (var statement = connection.prepareStatement("""
                INSERT INTO channels(id, guild_id, parent_id, name, type, position)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET parent_id = EXCLUDED.parent_id, name = EXCLUDED.name,
                    type = EXCLUDED.type, position = EXCLUDED.position
                """)) {
                statement.setObject(1, channel.id());
                statement.setObject(2, guild.id());
                statement.setObject(3, channel.parentId());
                statement.setString(4, channel.name());
                statement.setString(5, channel.type().name());
                statement.setInt(6, currentPosition);
                statement.executeUpdate();
            }
        }
    }

    private static void insertChannelOverwrites(Connection connection, Guild guild, Map<UUID, Channel> previous) throws SQLException {
        for (Channel channel : guild.channels()) {
            if (channel.equals(previous.get(channel.id()))) {
                continue;
            }
            for (PermissionOverwrite overwrite : channel.overwrites()) {
                try (var statement = connection.prepareStatement("""
                    INSERT INTO channel_role_overwrites(channel_id, role_id, allow, deny)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT (channel_id, role_id) DO UPDATE
                    SET allow = EXCLUDED.allow, deny = EXCLUDED.deny
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
}
