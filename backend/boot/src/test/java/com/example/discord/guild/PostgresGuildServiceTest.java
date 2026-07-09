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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
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

        PersistentGuildService service = new PersistentGuildService(snapshots);
        Guild guild = service.createGuild("Persisted Guild", ownerId);
        Channel channel = service.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Role manager = service.createRole(
            guild.id(),
            "manager",
            PermissionSet.empty().grant(Permission.MANAGE_CHANNELS)
        );
        service.addMember(guild.id(), memberId);
        service.assignRoleToMember(guild.id(), memberId, manager.id());

        PersistentGuildService reloaded = new PersistentGuildService(snapshots);

        assertThat(guild.name()).isEqualTo("Persisted Guild");
        assertThat(guild.ownerId()).isEqualTo(ownerId);
        assertThat(guild.members()).extracting(GuildMember::userId).containsExactly(ownerId, memberId);
        assertThat(guild.everyoneRole().name()).isEqualTo("@everyone");
        assertThat(guild.member(ownerId).roleIds()).contains(guild.everyoneRole().id());
        assertThat(reloaded.guildIdsForMember(ownerId)).containsExactly(guild.id());
        assertThat(reloaded.guildIdsForMember(memberId)).containsExactly(guild.id());
        assertThat(reloaded.channel(guild.id(), channel.id()).name()).isEqualTo("general");
        assertThat(reloaded.roles(guild.id()))
            .extracting(Role::name)
            .contains("@everyone", "manager");
        assertThat(reloaded.canManageChannels(guild.id(), memberId)).isTrue();
    }

    @Test
    void filtersVisibleChannelsUsingPersistedEffectiveViewChannelPermission() throws Exception {
        UUID ownerId = UUID.randomUUID();
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8), "Owner");

        PersistentGuildService service = new PersistentGuildService(snapshots);
        Guild guild = service.createGuild("Persisted Guild", ownerId);
        Channel general = service.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);
        Channel staff = service.createChannel(guild.id(), "staff", ChannelType.GUILD_TEXT, null);

        service.assignRolePermissions(guild.id(), guild.everyoneRole().id(), PermissionSet.empty().grant(Permission.VIEW_CHANNEL));
        service.addChannelRoleOverwrite(
            guild.id(),
            staff.id(),
            guild.everyoneRole().id(),
            PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );

        PersistentGuildService reloaded = new PersistentGuildService(snapshots);

        assertThat(reloaded.visibleChannels(guild.id(), ownerId)).extracting(Channel::id)
            .containsExactly(general.id());
    }

    @Test
    void administratorRoleSeesPersistedChannelEvenWhenEveryoneDenied() throws Exception {
        UUID ownerId = UUID.randomUUID();
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8), "Owner");

        PersistentGuildService service = new PersistentGuildService(snapshots);
        Guild guild = service.createGuild("Persisted Guild", ownerId);
        Channel adminOnly = service.createChannel(guild.id(), "admin-only", ChannelType.GUILD_TEXT, null);
        Role adminRole = service.createRole(guild.id(), "admin");

        service.assignRolePermissions(guild.id(), adminRole.id(), PermissionSet.empty().grant(Permission.ADMINISTRATOR));
        service.assignRoleToMember(guild.id(), ownerId, adminRole.id());
        service.addChannelRoleOverwrite(
            guild.id(),
            adminOnly.id(),
            guild.everyoneRole().id(),
            PermissionSet.empty(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL)
        );

        PersistentGuildService reloaded = new PersistentGuildService(snapshots);

        assertThat(reloaded.visibleChannels(guild.id(), ownerId)).extracting(Channel::id)
            .containsExactly(adminOnly.id());
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
