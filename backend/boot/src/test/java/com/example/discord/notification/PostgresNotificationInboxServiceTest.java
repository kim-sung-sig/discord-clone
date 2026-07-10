package com.example.discord.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.channel.ChannelType;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
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
class PostgresNotificationInboxServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000431");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000432");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000433");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000434");
    private static final UUID VISIBLE_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000435");
    private static final UUID HIDDEN_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000436");
    private static final Instant NOW = Instant.parse("2026-05-18T00:00:00Z");

    private JdbcNotificationInboxService service;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void cleanAndSeed() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM notification_items");
            statement.executeUpdate("DELETE FROM notification_preferences");
            statement.executeUpdate("DELETE FROM premium_entitlements");
            statement.executeUpdate("DELETE FROM channel_role_overwrites");
            statement.executeUpdate("DELETE FROM guild_member_roles");
            statement.executeUpdate("DELETE FROM channels");
            statement.executeUpdate("DELETE FROM guild_roles");
            statement.executeUpdate("DELETE FROM guild_members");
            statement.executeUpdate("DELETE FROM guilds");
            statement.executeUpdate("DELETE FROM auth_accounts");
            statement.executeUpdate("DELETE FROM users");
        }
        insertUser(AUTHOR_ID, "author");
        insertUser(VISIBLE_USER_ID, "visible");
        insertUser(HIDDEN_USER_ID, "hidden");
        insertGuildAndChannel();
        service = new JdbcNotificationInboxService(dataSource, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsMentionOnlyForVisibleMentionedRecipients() {
        service.recordMention(new MentionNotificationCommand(
            GUILD_ID,
            CHANNEL_ID,
            MESSAGE_ID,
            10L,
            AUTHOR_ID,
            Set.of(VISIBLE_USER_ID, HIDDEN_USER_ID, AUTHOR_ID),
            Set.of(VISIBLE_USER_ID),
            "mentioned you"
        ));

        assertThat(service.inbox(VISIBLE_USER_ID))
            .extracting(NotificationItem::kind)
            .containsExactly(NotificationKind.MENTION);
        assertThat(service.inbox(HIDDEN_USER_ID)).isEmpty();
        assertThat(service.inbox(AUTHOR_ID)).isEmpty();
        assertThat(service.unreadCount(VISIBLE_USER_ID)).isEqualTo(1L);
    }

    @Test
    void preferenceSuppressesNewMentionItems() {
        service.updatePreferences(VISIBLE_USER_ID, new NotificationPreferences(false, true, true));

        service.recordMention(new MentionNotificationCommand(
            GUILD_ID,
            CHANNEL_ID,
            MESSAGE_ID,
            10L,
            AUTHOR_ID,
            Set.of(VISIBLE_USER_ID),
            Set.of(VISIBLE_USER_ID),
            "mentioned you"
        ));

        assertThat(service.inbox(VISIBLE_USER_ID)).isEmpty();
    }

    @Test
    void dmAndServerNotificationsContributeToUnreadCountNewestFirst() {
        service.recordDirectMessage(VISIBLE_USER_ID, CHANNEL_ID, UUID.randomUUID(), 11L, "new dm");
        service.recordServerNotification(VISIBLE_USER_ID, GUILD_ID, CHANNEL_ID, UUID.randomUUID(), 12L, "server event");

        List<NotificationItem> inbox = service.inbox(VISIBLE_USER_ID);

        assertThat(inbox).extracting(NotificationItem::kind)
            .containsExactly(NotificationKind.SERVER, NotificationKind.DM);
        assertThat(service.unreadCount(VISIBLE_USER_ID)).isEqualTo(2L);
    }

    @Test
    void markChannelReadClearsUnreadItemsUpToSequence() {
        service.recordDirectMessage(VISIBLE_USER_ID, CHANNEL_ID, UUID.randomUUID(), 11L, "old dm");
        service.recordDirectMessage(VISIBLE_USER_ID, CHANNEL_ID, UUID.randomUUID(), 12L, "new dm");

        service.markChannelRead(VISIBLE_USER_ID, CHANNEL_ID, 11L);

        assertThat(service.unreadCount(VISIBLE_USER_ID)).isEqualTo(1L);
        assertThat(service.inbox(VISIBLE_USER_ID))
            .filteredOn(NotificationItem::read)
            .extracting(NotificationItem::sequence)
            .containsExactly(11L);
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
            guild.setObject(1, GUILD_ID);
            guild.setString(2, "notifications");
            guild.setObject(3, AUTHOR_ID);
            guild.executeUpdate();
            channel.setObject(1, CHANNEL_ID);
            channel.setObject(2, GUILD_ID);
            channel.setString(3, "general");
            channel.setString(4, ChannelType.GUILD_TEXT.name());
            channel.executeUpdate();
        }
    }
}
