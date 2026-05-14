package com.example.discord.invite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcInviteSnapshotStore implements InviteSnapshotStore {
    private final DataSource dataSource;

    JdbcInviteSnapshotStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Invite> loadAll() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT code, guild_id, channel_id, creator_id, max_age_seconds, max_uses, temporary, deleted, created_at, deleted_at
                 FROM invites
                 ORDER BY created_at, code
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            List<Invite> invites = new ArrayList<>();
            while (resultSet.next()) {
                String code = resultSet.getString("code");
                Timestamp deletedAt = resultSet.getTimestamp("deleted_at");
                invites.add(new Invite(
                    code,
                    resultSet.getObject("guild_id", UUID.class),
                    resultSet.getObject("channel_id", UUID.class),
                    resultSet.getObject("creator_id", UUID.class),
                    resultSet.getLong("max_age_seconds"),
                    resultSet.getInt("max_uses"),
                    resultSet.getBoolean("temporary"),
                    loadRoleGrants(connection, code),
                    resultSet.getTimestamp("created_at").toInstant(),
                    deletedAt == null ? null : deletedAt.toInstant(),
                    loadAcceptedMembers(connection, code)
                ));
            }
            return List.copyOf(invites);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load invite snapshots", exception);
        }
    }

    @Override
    public void save(Invite invite) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertInvite(connection, invite);
                replaceRoleGrants(connection, invite);
                replaceAcceptances(connection, invite);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save invite snapshot", exception);
        }
    }

    private static void upsertInvite(Connection connection, Invite invite) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO invites(code, guild_id, channel_id, creator_id, max_age_seconds, max_uses, uses, temporary, deleted, created_at, deleted_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (code) DO UPDATE
            SET max_age_seconds = EXCLUDED.max_age_seconds,
                max_uses = EXCLUDED.max_uses,
                uses = EXCLUDED.uses,
                temporary = EXCLUDED.temporary,
                deleted = EXCLUDED.deleted,
                deleted_at = EXCLUDED.deleted_at
            """)) {
            statement.setString(1, invite.code());
            statement.setObject(2, invite.guildId());
            statement.setObject(3, invite.channelId());
            statement.setObject(4, invite.creatorId());
            statement.setLong(5, invite.maxAgeSeconds());
            statement.setInt(6, invite.maxUses());
            statement.setInt(7, invite.uses());
            statement.setBoolean(8, invite.temporary());
            statement.setBoolean(9, invite.deletedAt() != null);
            statement.setTimestamp(10, Timestamp.from(invite.createdAt()));
            statement.setTimestamp(11, invite.deletedAt() == null ? null : Timestamp.from(invite.deletedAt()));
            statement.executeUpdate();
        }
    }

    private static List<UUID> loadRoleGrants(Connection connection, String code) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT role_id
            FROM invite_role_grants
            WHERE code = ?
            ORDER BY role_id
            """)) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<UUID> roleIds = new ArrayList<>();
                while (resultSet.next()) {
                    roleIds.add(resultSet.getObject("role_id", UUID.class));
                }
                return roleIds;
            }
        }
    }

    private static Set<UUID> loadAcceptedMembers(Connection connection, String code) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT member_id
            FROM invite_acceptances
            WHERE code = ?
            ORDER BY accepted_at, member_id
            """)) {
            statement.setString(1, code);
            try (ResultSet resultSet = statement.executeQuery()) {
                Set<UUID> memberIds = new LinkedHashSet<>();
                while (resultSet.next()) {
                    memberIds.add(resultSet.getObject("member_id", UUID.class));
                }
                return memberIds;
            }
        }
    }

    private static void replaceRoleGrants(Connection connection, Invite invite) throws SQLException {
        deleteByCode(connection, "DELETE FROM invite_role_grants WHERE code = ?", invite.code());
        for (UUID roleId : invite.roleGrantIds()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO invite_role_grants(code, role_id)
                VALUES (?, ?)
                """)) {
                statement.setString(1, invite.code());
                statement.setObject(2, roleId);
                statement.executeUpdate();
            }
        }
    }

    private static void replaceAcceptances(Connection connection, Invite invite) throws SQLException {
        deleteByCode(connection, "DELETE FROM invite_acceptances WHERE code = ?", invite.code());
        for (UUID memberId : invite.acceptedMemberIds()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO invite_acceptances(code, member_id)
                VALUES (?, ?)
                """)) {
                statement.setString(1, invite.code());
                statement.setObject(2, memberId);
                statement.executeUpdate();
            }
        }
    }

    private static void deleteByCode(Connection connection, String sql, String code) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            statement.executeUpdate();
        }
    }
}
