package com.example.discord.guild;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
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
class PostgresGuildServiceTest {
    @Autowired
    private GuildSnapshotStore snapshots;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanGuildTables() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM channel_role_overwrites");
            statement.executeUpdate("DELETE FROM guild_member_roles");
            statement.executeUpdate("DELETE FROM channels");
            statement.executeUpdate("DELETE FROM guild_roles");
            statement.executeUpdate("DELETE FROM guild_members");
            statement.executeUpdate("DELETE FROM guilds");
            statement.executeUpdate("DELETE FROM auth_accounts");
            statement.executeUpdate("DELETE FROM users");
        }
    }

    @Test
    void persistsGuildAggregateAndReloadsItIntoFreshService() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8), "Owner");
        insertUser(memberId, "member" + memberId.toString().substring(0, 8), "Member");

        InMemoryGuildService service = new PersistentGuildService(snapshots);
        Guild guild = service.createGuild("Persisted Guild", ownerId);
        Channel channel = service.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Role manager = service.createRole(
            guild.id(),
            "manager",
            PermissionSet.empty().grant(Permission.MANAGE_CHANNELS)
        );
        service.addMember(guild.id(), memberId);
        service.assignRoleToMember(guild.id(), memberId, manager.id());

        InMemoryGuildService reloaded = new PersistentGuildService(snapshots);

        assertThat(reloaded.guildIdsForMember(ownerId)).containsExactly(guild.id());
        assertThat(reloaded.guildIdsForMember(memberId)).containsExactly(guild.id());
        assertThat(reloaded.channel(guild.id(), channel.id()).name()).isEqualTo("general");
        assertThat(reloaded.roles(guild.id()))
            .extracting(Role::name)
            .contains("@everyone", "manager");
        assertThat(reloaded.canManageChannels(guild.id(), memberId)).isTrue();
    }

    private void insertUser(UUID id, String username, String displayName) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO users(id, username, display_name, created_at, updated_at)
                 VALUES (?, ?, ?, ?, ?)
                 """)) {
            Timestamp createdAt = Timestamp.from(Instant.parse("2026-01-01T00:00:00Z"));
            statement.setObject(1, id);
            statement.setString(2, username);
            statement.setString(3, displayName);
            statement.setTimestamp(4, createdAt);
            statement.setTimestamp(5, createdAt);
            statement.executeUpdate();
        }
    }
}
