package com.example.discord.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
class PostgresDirectMessageServiceTest {
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-000000000491");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-000000000492");

    @Autowired private DataSource dataSource;
    @Autowired private JdbcDirectMessageService service;

    @BeforeEach
    void seed() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM notification_items WHERE user_id IN ('" + ALICE + "', '" + BOB + "')");
            statement.executeUpdate("DELETE FROM dm_read_markers WHERE user_id IN ('" + ALICE + "', '" + BOB + "')");
            statement.executeUpdate("DELETE FROM dm_messages WHERE sender_id IN ('" + ALICE + "', '" + BOB + "')");
            statement.executeUpdate("DELETE FROM dm_channels WHERE first_user_id IN ('" + ALICE + "', '" + BOB + "')");
            statement.executeUpdate("DELETE FROM social_blocks WHERE blocker_id IN ('" + ALICE + "', '" + BOB + "')");
            statement.executeUpdate("DELETE FROM users WHERE id IN ('" + ALICE + "', '" + BOB + "')");
        }
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
            "INSERT INTO users(id, username, display_name) VALUES (?, ?, ?)")) {
            for (UUID user : new UUID[] {ALICE, BOB}) {
                statement.setObject(1, user);
                statement.setString(2, "dm_" + user.toString().substring(34));
                statement.setString(3, "DM User");
                statement.executeUpdate();
            }
        }
    }

    @Test
    void persistsMessageAndRecipientNotificationAcrossServiceInstances() {
        var first = service.send(ALICE, BOB, "hello");
        var second = service.send(ALICE, BOB, "again");

        var restarted = new JdbcDirectMessageService(dataSource);
        assertThat(restarted.messages(BOB, ALICE, null, 50))
            .extracting(JdbcDirectMessageService.DirectMessage::content)
            .containsExactly("again", "hello");
        assertThat(restarted.messages(BOB, ALICE, null, 50))
            .extracting(JdbcDirectMessageService.DirectMessage::sequence)
            .containsExactly(2L, 1L);
        assertThat(first.channelId()).isEqualTo(second.channelId());
        assertThat(unreadNotifications()).isEqualTo(2);
        assertThat(restarted.markRead(BOB, ALICE, 1)).isEqualTo(1);
        assertThat(unreadNotifications()).isEqualTo(1);
        assertThat(new JdbcDirectMessageService(dataSource).readSequence(BOB, ALICE)).isEqualTo(1);
    }

    @Test
    void persistedBlockPreventsSendAfterServiceRestart() {
        service.block(BOB, ALICE);
        assertThatThrownBy(() -> new JdbcDirectMessageService(dataSource).send(ALICE, BOB, "blocked"))
            .isInstanceOf(SocialPolicyException.class);
    }

    @Test
    void persistedBlockRejectsBothParticipantsReadsWithoutChangingUnreadState() {
        service.send(ALICE, BOB, "private history");
        service.block(BOB, ALICE);
        var restarted = new JdbcDirectMessageService(dataSource);
        for (UUID[] pair : new UUID[][] {{ALICE, BOB}, {BOB, ALICE}}) {
            assertThatThrownBy(() -> restarted.messages(pair[0], pair[1], null, 50))
                .isInstanceOf(SocialPolicyException.class);
            assertThatThrownBy(() -> restarted.readSequence(pair[0], pair[1]))
                .isInstanceOf(SocialPolicyException.class);
            assertThatThrownBy(() -> restarted.markRead(pair[0], pair[1], 1))
                .isInstanceOf(SocialPolicyException.class);
        }
        assertThat(unreadNotifications()).isEqualTo(1);
    }

    @Test
    void readingOlderSequenceDoesNotMoveMarkerBackward() {
        service.send(ALICE, BOB, "first");
        service.send(ALICE, BOB, "second");
        assertThat(service.markRead(BOB, ALICE, 2)).isEqualTo(2);
        assertThat(service.markRead(BOB, ALICE, 1)).isEqualTo(2);
        assertThat(new JdbcDirectMessageService(dataSource).readSequence(BOB, ALICE)).isEqualTo(2);
        assertThat(unreadNotifications()).isZero();
    }

    @Test
    void notificationInsertFailureRollsBackMessageAndSequence() throws Exception {
        service.channel(ALICE, BOB);
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE notification_items ADD CONSTRAINT t43_reject_dm CHECK (kind <> 'DM') NOT VALID");
            try {
                assertThatThrownBy(() -> service.send(ALICE, BOB, "must roll back"))
                    .isInstanceOf(IllegalStateException.class);
                assertThat(service.messages(BOB, ALICE, null, 50)).isEmpty();
                assertThat(service.readSequence(BOB, ALICE)).isZero();
                assertThat(unreadNotifications()).isZero();
            } finally {
                statement.execute("ALTER TABLE notification_items DROP CONSTRAINT t43_reject_dm");
            }
        }
        service.send(ALICE, BOB, "retry");
        assertThat(service.messages(BOB, ALICE, null, 50)).extracting(JdbcDirectMessageService.DirectMessage::sequence)
            .containsExactly(1L);
    }

    @Test
    void disabledNotificationsStillPersistDirectMessage() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
            INSERT INTO notification_preferences(user_id, mentions_enabled, direct_messages_enabled, server_notifications_enabled)
            VALUES (?, TRUE, FALSE, TRUE)
            """)) {
            statement.setObject(1, BOB);
            statement.executeUpdate();
        }
        service.send(ALICE, BOB, "not lost");
        assertThat(service.messages(BOB, ALICE, null, 50)).hasSize(1);
        assertThat(unreadNotifications()).isZero();
    }

    @Test
    void rejectsFutureReadSequence() {
        service.send(ALICE, BOB, "hello");
        assertThatThrownBy(() -> service.markRead(BOB, ALICE, 2)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnknownRecipientBeforeCreatingChannel() {
        UUID unknown = UUID.fromString("00000000-0000-0000-0000-000000000499");
        assertThatThrownBy(() -> service.send(ALICE, unknown, "hello"))
            .isInstanceOf(SocialNotFoundException.class);
    }

    private long unreadNotifications() {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement(
            "SELECT COUNT(*) FROM notification_items WHERE user_id = ? AND kind = 'DM' AND read = FALSE")) {
            statement.setObject(1, BOB);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
