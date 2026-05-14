package com.example.discord.invite;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
class PostgresInviteServiceTest {
    @Autowired
    private InviteSnapshotStore snapshots;

    @Autowired
    private DataSource dataSource;

    private UUID ownerId;
    private UUID memberId;
    private UUID guildId;
    private UUID channelId;

    @BeforeEach
    void cleanAndSeed() throws Exception {
        ownerId = UUID.randomUUID();
        memberId = UUID.randomUUID();
        guildId = UUID.randomUUID();
        channelId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM invite_acceptances");
            statement.executeUpdate("DELETE FROM invite_role_grants");
            statement.executeUpdate("DELETE FROM invites");
            statement.executeUpdate("DELETE FROM channel_role_overwrites");
            statement.executeUpdate("DELETE FROM guild_member_roles");
            statement.executeUpdate("DELETE FROM channels");
            statement.executeUpdate("DELETE FROM guild_roles");
            statement.executeUpdate("DELETE FROM guild_members");
            statement.executeUpdate("DELETE FROM guilds");
            statement.executeUpdate("DELETE FROM auth_accounts");
            statement.executeUpdate("DELETE FROM users");
        }
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8));
        insertUser(memberId, "member" + memberId.toString().substring(0, 8));
        insertGuildAndChannel();
    }

    @Test
    void persistsInvitesAndReloadsAcceptedMembersAndDeletion() {
        InMemoryInviteService service = new PersistentInviteService(snapshots, java.time.Clock.systemUTC());
        Invite created = service.create(new CreateInviteCommand(guildId, channelId, ownerId, 0, 5, false, java.util.List.of()));
        service.accept(created.code(), memberId);
        service.delete(created.code());

        InMemoryInviteService reloaded = new PersistentInviteService(snapshots, java.time.Clock.systemUTC());
        Invite invite = reloaded.get(created.code());

        assertThat(invite.uses()).isEqualTo(1);
        assertThat(invite.acceptedMemberIds()).containsExactly(memberId);
        assertThat(invite.deletedAt()).isNotNull();
    }

    private void insertUser(UUID id, String username) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO users(id, username, display_name, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?)
                 """)) {
            Timestamp createdAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, username);
            statement.setTimestamp(4, createdAt);
            statement.setTimestamp(5, createdAt);
            statement.executeUpdate();
        }
    }

    private void insertGuildAndChannel() throws Exception {
        try (var connection = dataSource.getConnection();
             var guild = connection.prepareStatement("INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)");
             var channel = connection.prepareStatement("INSERT INTO channels(id, guild_id, name, type) VALUES (?, ?, ?, ?)")) {
            guild.setObject(1, guildId);
            guild.setString(2, "guild");
            guild.setObject(3, ownerId);
            guild.executeUpdate();
            channel.setObject(1, channelId);
            channel.setObject(2, guildId);
            channel.setString(3, "general");
            channel.setString(4, "GUILD_TEXT");
            channel.executeUpdate();
        }
    }
}
