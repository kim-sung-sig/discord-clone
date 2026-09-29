package com.example.discord.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class PostgresChannelReadServiceTest {
    private static final UUID OWNER = UUID.fromString("00000000-0000-0000-0000-000000000481");
    private static final UUID GUILD = UUID.fromString("00000000-0000-0000-0000-000000000482");
    private static final UUID CHANNEL = UUID.fromString("00000000-0000-0000-0000-000000000483");
    private static final UUID MESSAGE = UUID.fromString("00000000-0000-0000-0000-000000000484");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcChannelReadService service;

    @BeforeEach
    void seed() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM notification_items WHERE channel_id = '" + CHANNEL + "'");
            statement.executeUpdate("DELETE FROM channel_read_markers WHERE channel_id = '" + CHANNEL + "'");
            statement.executeUpdate("DELETE FROM messages WHERE channel_id = '" + CHANNEL + "'");
            statement.executeUpdate("DELETE FROM channels WHERE id = '" + CHANNEL + "'");
            statement.executeUpdate("DELETE FROM guilds WHERE id = '" + GUILD + "'");
            statement.executeUpdate("DELETE FROM users WHERE id = '" + OWNER + "'");
        }
        try (var connection = dataSource.getConnection()) {
            try (var statement = connection.prepareStatement("INSERT INTO users(id, username, display_name) VALUES (?, ?, ?)")) {
                statement.setObject(1, OWNER);
                statement.setString(2, "read_marker_owner");
                statement.setString(3, "Read Marker Owner");
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)")) {
                statement.setObject(1, GUILD);
                statement.setString(2, "read-marker-guild");
                statement.setObject(3, OWNER);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO channels(id, guild_id, name, type) VALUES (?, ?, ?, 'GUILD_TEXT')")) {
                statement.setObject(1, CHANNEL);
                statement.setObject(2, GUILD);
                statement.setString(3, "read-marker-channel");
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("INSERT INTO messages(id, guild_id, channel_id, author_id, sequence, content) VALUES (?, ?, ?, ?, 1, 'hi')")) {
                statement.setObject(1, MESSAGE);
                statement.setObject(2, GUILD);
                statement.setObject(3, CHANNEL);
                statement.setObject(4, OWNER);
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                INSERT INTO notification_items(id, user_id, guild_id, channel_id, source_id, sequence, kind, summary, read, created_at)
                VALUES (?, ?, ?, ?, ?, 1, 'MENTION', 'hi', FALSE, ?)
                """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, OWNER);
                statement.setObject(3, GUILD);
                statement.setObject(4, CHANNEL);
                statement.setObject(5, MESSAGE);
                statement.setTimestamp(6, Timestamp.from(Instant.now()));
                statement.executeUpdate();
            }
        }
    }

    @Test
    void keepsReadMarkerAcrossServiceInstancesAndMarksNotifications() {
        assertThat(service.markRead(CHANNEL, OWNER, 1).lastReadSequence()).isEqualTo(1);
        assertThat(new JdbcChannelReadService(dataSource).readMarker(CHANNEL, OWNER).lastReadSequence()).isEqualTo(1);
        assertThat(service.markRead(CHANNEL, OWNER, 0).lastReadSequence()).isEqualTo(1);
        assertThat(service.unreadCount(CHANNEL, OWNER)).isZero();
        assertThat(unreadNotifications()).isZero();
    }

    @Test
    void rejectsSequenceBeyondPersistedMessagesWithoutChangingState() {
        assertThatThrownBy(() -> service.markRead(CHANNEL, OWNER, 2)).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.readMarker(CHANNEL, OWNER).lastReadSequence()).isZero();
        assertThat(unreadNotifications()).isEqualTo(1);
    }

    private long unreadNotifications() {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM notification_items WHERE channel_id = ? AND read = FALSE")) {
            statement.setObject(1, CHANNEL);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
