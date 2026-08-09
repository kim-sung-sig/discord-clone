package com.example.discord.guild;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.channel.ChannelType;
import com.example.discord.permission.Permission;
import com.example.discord.permission.PermissionSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
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
            statement.executeUpdate("DELETE FROM consumer_inbox");
            statement.executeUpdate("DELETE FROM authorization_projection");
            statement.executeUpdate("DELETE FROM authorization_watermark");
            statement.executeUpdate("DELETE FROM authorization_projection_outbox");
            statement.executeUpdate("DELETE FROM guild_authorization_versions");
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
        assertThat(countRows("authorization_projection_outbox")).isGreaterThan(0);
        assertThat(countRows("guild_authorization_versions")).isEqualTo(1);
    }

    private int countRows(String table) throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT count(*) FROM " + table)) {
            resultSet.next();
            return resultSet.getInt(1);
        }
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

    @Test
    void projectionUsesOnlyOverwritesRelevantToEachMemberRole() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8), "Owner");
        insertUser(memberId, "member" + memberId.toString().substring(0, 8), "Member");
        PersistentGuildService service = new PersistentGuildService(snapshots);
        Guild guild = service.createGuild("projection-overwrite", ownerId);
        service.addMember(guild.id(), memberId);
        Channel channel = service.createChannel(guild.id(), "restricted", ChannelType.GUILD_TEXT, null);
        Role role = service.createRole(guild.id(), "allowed");
        service.assignRoleToMember(guild.id(), ownerId, role.id());
        service.addChannelRoleOverwrite(guild.id(), channel.id(), guild.everyoneRole().id(),
            PermissionSet.empty(), PermissionSet.empty().grant(Permission.VIEW_CHANNEL));
        service.addChannelRoleOverwrite(guild.id(), channel.id(), role.id(),
            PermissionSet.empty().grant(Permission.VIEW_CHANNEL), PermissionSet.empty());

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT subject_id, permission_bits FROM authorization_projection_outbox
                 WHERE guild_id = ? AND resource_type = 'CHANNEL' AND resource_id = ?
                   AND audience = 'GATEWAY' AND event_kind = 'PROJECTION_UPDATED'
                   AND permission_version = (
                       SELECT MAX(permission_version) FROM authorization_projection_outbox
                       WHERE guild_id = ? AND resource_type = 'CHANNEL' AND resource_id = ?
                         AND audience = 'GATEWAY' AND event_kind = 'PROJECTION_UPDATED'
                   )
                 ORDER BY subject_id
                 """)) {
            statement.setObject(1, guild.id());
            statement.setObject(2, channel.id());
            statement.setObject(3, guild.id());
            statement.setObject(4, channel.id());
            try (var rows = statement.executeQuery()) {
                Map<UUID, Long> permissions = new LinkedHashMap<>();
                while (rows.next()) permissions.put(rows.getObject(1, UUID.class), rows.getLong(2));
                assertThat(permissions.get(ownerId) & Permission.VIEW_CHANNEL.bit()).isNotZero();
                assertThat(permissions.get(memberId) & Permission.VIEW_CHANNEL.bit()).isZero();
            }
        }
    }

    @Test
    void commitsPermissionVersionAndAudienceOutboxRowsWithGuildMutation() throws Exception {
        UUID ownerId = UUID.randomUUID();
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8), "Owner");

        PersistentGuildService service = new PersistentGuildService(snapshots);
        Guild guild = service.createGuild("Persisted Guild", ownerId);
        Channel channel = service.createChannel(guild.id(), "general", ChannelType.GUILD_TEXT, null);

        try (var connection = dataSource.getConnection();
             var version = connection.prepareStatement("SELECT permission_version FROM guild_authorization_versions WHERE guild_id = ?");
             var outbox = connection.prepareStatement("""
                 SELECT COUNT(*) FILTER (WHERE event_kind = 'PROJECTION_UPDATED' AND permission_version = 2),
                        COUNT(*) FILTER (WHERE event_kind = 'WATERMARK_ADVANCED' AND permission_version = 2),
                        MIN(permission_version), MAX(permission_version), COUNT(DISTINCT audience)
                 FROM authorization_projection_outbox WHERE guild_id = ?
                 """)) {
            version.setObject(1, guild.id());
            try (var rows = version.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(2L);
            }
            outbox.setObject(1, guild.id());
            try (var rows = outbox.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getLong(1)).isEqualTo(4L);
                assertThat(rows.getLong(2)).isEqualTo(4L);
                assertThat(rows.getLong(3)).isEqualTo(1L);
                assertThat(rows.getLong(4)).isEqualTo(2L);
                assertThat(rows.getLong(5)).isEqualTo(4L);
            }
        }

        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT audience, resource_type FROM authorization_projection_outbox
                 WHERE guild_id = ? AND event_kind = 'PROJECTION_UPDATED' AND permission_version = 2
                 ORDER BY audience
                 """)) {
            statement.setObject(1, guild.id());
            try (var rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("GATEWAY");
                assertThat(rows.getString(2)).isEqualTo("CHANNEL");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("MESSAGE");
                assertThat(rows.getString(2)).isEqualTo("CHANNEL");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("NOTIFICATION");
                assertThat(rows.getString(2)).isEqualTo("GUILD");
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString(1)).isEqualTo("WEBSOCKET");
                assertThat(rows.getString(2)).isEqualTo("CHANNEL");
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void rollsBackGuildAndAuthorizationRowsWhenChildPersistenceFails() throws Exception {
        UUID ownerId = UUID.randomUUID();
        insertUser(ownerId, "owner" + ownerId.toString().substring(0, 8), "Owner");
        PersistentGuildService service = new PersistentGuildService(snapshots);
        Guild guild = service.createGuild("rollback", ownerId);
        int guildRows = countRows("guilds");
        int channelRows = countRows("channels");
        int outboxRows = countRows("authorization_projection_outbox");
        int versionRows = countRows("guild_authorization_versions");

        assertThatThrownBy(() -> service.createChannel(
            guild.id(), "invalid-parent", ChannelType.GUILD_TEXT, UUID.randomUUID()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(countRows("guilds")).isEqualTo(guildRows);
        assertThat(countRows("channels")).isEqualTo(channelRows);
        assertThat(countRows("authorization_projection_outbox")).isEqualTo(outboxRows);
        assertThat(countRows("guild_authorization_versions")).isEqualTo(versionRows);
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
