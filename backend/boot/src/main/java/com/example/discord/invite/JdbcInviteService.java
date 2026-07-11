package com.example.discord.invite;

import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

class JdbcInviteService implements InviteService {
    private static final char[] CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();
    private final DataSource dataSource;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    JdbcInviteService(DataSource dataSource, Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
    }

    public Invite create(CreateInviteCommand command) {
        Instant now = clock.instant();
        for (int attempt = 0; attempt < 5; attempt++) {
            String code = nextCode();
            try (Connection connection = dataSource.getConnection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement invite = connection.prepareStatement("INSERT INTO invites(code, guild_id, channel_id, creator_id, max_age_seconds, max_uses, uses, temporary, deleted, created_at) VALUES (?, ?, ?, ?, ?, ?, 0, ?, FALSE, ?)")) {
                    invite.setString(1, code); invite.setObject(2, command.guildId()); invite.setObject(3, command.channelId()); invite.setObject(4, command.creatorId()); invite.setLong(5, command.maxAgeSeconds()); invite.setInt(6, command.maxUses()); invite.setBoolean(7, command.temporary()); invite.setTimestamp(8, Timestamp.from(now));
                    invite.executeUpdate();
                    insertRoleGrants(connection, code, command.roleGrantIds());
                    connection.commit();
                    return new Invite(code, command.guildId(), command.channelId(), command.creatorId(), command.maxAgeSeconds(), command.maxUses(), command.temporary(), command.roleGrantIds(), now, null, Set.of());
                } catch (SQLException exception) { connection.rollback(); if ("23505".equals(exception.getSQLState())) continue; throw exception; }
            } catch (SQLException exception) { throw new IllegalStateException("failed to create invite", exception); }
        }
        throw new IllegalStateException("failed to allocate invite code");
    }

    public Invite preview(String code) {
        Invite invite = get(code);
        requireUsable(invite);
        return invite;
    }

    public Invite get(String code) {
        try (Connection connection = dataSource.getConnection()) { return readInvite(connection, code, false); }
        catch (SQLException exception) { throw new IllegalStateException("failed to read invite", exception); }
    }

    public InviteAcceptResult accept(String code, UUID memberId) {
        if (memberId == null) throw new IllegalArgumentException("memberId is required");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Invite invite = readInvite(connection, code, true);
                requireUsable(invite);
                if (invite.acceptedMemberIds().contains(memberId)) { connection.commit(); return new InviteAcceptResult(invite, memberId, true); }
                if (invite.maxUses() > 0 && invite.uses() >= invite.maxUses()) throw new InviteMaxUsesExceededException();
                try (PreparedStatement acceptance = connection.prepareStatement("INSERT INTO invite_acceptances(code, member_id, accepted_at) VALUES (?, ?, ?)") ) {
                    acceptance.setString(1, code); acceptance.setObject(2, memberId); acceptance.setTimestamp(3, Timestamp.from(clock.instant())); acceptance.executeUpdate();
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE invites SET uses = uses + 1 WHERE code = ?")) { update.setString(1, code); update.executeUpdate(); }
                Invite accepted = readInvite(connection, code, false);
                connection.commit();
                return new InviteAcceptResult(accepted, memberId, false);
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("failed to accept invite", exception); }
    }

    public Invite delete(String code) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Invite invite = readInvite(connection, code, true);
                if (invite.deletedAt() == null) try (PreparedStatement update = connection.prepareStatement("UPDATE invites SET deleted = TRUE, deleted_at = ? WHERE code = ?")) { update.setTimestamp(1, Timestamp.from(clock.instant())); update.setString(2, code); update.executeUpdate(); }
                Invite deleted = readInvite(connection, code, false); connection.commit(); return deleted;
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("failed to delete invite", exception); }
    }

    private Invite readInvite(Connection connection, String code, boolean lock) throws SQLException {
        String sql = "SELECT code, guild_id, channel_id, creator_id, max_age_seconds, max_uses, temporary, created_at, deleted_at FROM invites WHERE code = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, code);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) throw new InviteNotFoundException();
                Timestamp deletedAt = result.getTimestamp("deleted_at");
                return new Invite(code, result.getObject("guild_id", UUID.class), result.getObject("channel_id", UUID.class), result.getObject("creator_id", UUID.class), result.getLong("max_age_seconds"), result.getInt("max_uses"), result.getBoolean("temporary"), roleGrants(connection, code), result.getTimestamp("created_at").toInstant(), deletedAt == null ? null : deletedAt.toInstant(), acceptances(connection, code));
            }
        }
    }

    private static List<UUID> roleGrants(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT role_id FROM invite_role_grants WHERE code = ? ORDER BY role_id")) { statement.setString(1, code); try (ResultSet result = statement.executeQuery()) { List<UUID> ids = new ArrayList<>(); while (result.next()) ids.add(result.getObject(1, UUID.class)); return ids; } }
    }
    private static Set<UUID> acceptances(Connection connection, String code) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT member_id FROM invite_acceptances WHERE code = ? ORDER BY accepted_at, member_id")) { statement.setString(1, code); try (ResultSet result = statement.executeQuery()) { Set<UUID> ids = new LinkedHashSet<>(); while (result.next()) ids.add(result.getObject(1, UUID.class)); return ids; } }
    }
    private static void insertRoleGrants(Connection connection, String code, List<UUID> roleIds) throws SQLException {
        if (roleIds == null) return;
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO invite_role_grants(code, role_id) VALUES (?, ?)")) { for (UUID roleId : roleIds) { statement.setString(1, code); statement.setObject(2, roleId); statement.addBatch(); } statement.executeBatch(); }
    }
    private void requireUsable(Invite invite) {
        if (invite.deletedAt() != null) throw new InviteDeletedException();
        if (invite.maxAgeSeconds() > 0 && !clock.instant().isBefore(invite.createdAt().plusSeconds(invite.maxAgeSeconds()))) throw new InviteExpiredException();
    }
    private String nextCode() { StringBuilder code = new StringBuilder(8); for (int index = 0; index < 8; index++) code.append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]); return code.toString(); }
}
