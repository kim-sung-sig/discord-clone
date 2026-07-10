package com.example.discord.notification;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class JdbcNotificationInboxService {
    private final DataSource dataSource;
    private final Clock clock;

    JdbcNotificationInboxService(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    void recordMention(MentionNotificationCommand command) {
        for (UUID userId : command.mentionedUserIds()) {
            if (userId.equals(command.authorId()) || !command.visibleRecipientIds().contains(userId)) {
                continue;
            }
            addItem(
                userId,
                command.guildId(),
                command.channelId(),
                command.messageId(),
                command.sequence(),
                NotificationKind.MENTION,
                command.summary()
            );
        }
    }

    void recordDirectMessage(UUID userId, UUID channelId, UUID messageId, long sequence, String summary) {
        addItem(userId, null, channelId, messageId, sequence, NotificationKind.DM, summary);
    }

    void recordServerNotification(
        UUID userId,
        UUID guildId,
        UUID channelId,
        UUID sourceId,
        long sequence,
        String summary
    ) {
        addItem(userId, guildId, channelId, sourceId, sequence, NotificationKind.SERVER, summary);
    }

    void updatePreferences(UUID userId, NotificationPreferences preferences) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(preferences, "preferences must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO notification_preferences(
                     user_id, mentions_enabled, direct_messages_enabled, server_notifications_enabled
                 )
                 VALUES (?, ?, ?, ?)
                 ON CONFLICT (user_id) DO UPDATE
                 SET mentions_enabled = EXCLUDED.mentions_enabled,
                     direct_messages_enabled = EXCLUDED.direct_messages_enabled,
                     server_notifications_enabled = EXCLUDED.server_notifications_enabled,
                     updated_at = NOW()
                 """)) {
            statement.setObject(1, userId);
            statement.setBoolean(2, preferences.mentionsEnabled());
            statement.setBoolean(3, preferences.directMessagesEnabled());
            statement.setBoolean(4, preferences.serverNotificationsEnabled());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save notification preferences", exception);
        }
    }

    List<NotificationItem> inbox(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, user_id, guild_id, channel_id, source_id, sequence, kind, summary, read, created_at
                 FROM notification_items
                 WHERE user_id = ?
                 ORDER BY sequence DESC, created_at DESC, id
                 """)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<NotificationItem> items = new ArrayList<>();
                while (resultSet.next()) {
                    items.add(item(resultSet));
                }
                return List.copyOf(items);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list notification inbox", exception);
        }
    }

    long unreadCount(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT COUNT(*)
                 FROM notification_items
                 WHERE user_id = ?
                   AND read = FALSE
                 """)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count unread notifications", exception);
        }
    }

    void markChannelRead(UUID userId, UUID channelId, long lastReadSequence) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        if (lastReadSequence < 0) {
            throw new IllegalArgumentException("lastReadSequence must not be negative");
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE notification_items
                 SET read = TRUE,
                     updated_at = NOW()
                 WHERE user_id = ?
                   AND channel_id = ?
                   AND sequence <= ?
                   AND read = FALSE
                 """)) {
            statement.setObject(1, userId);
            statement.setObject(2, channelId);
            statement.setLong(3, lastReadSequence);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark notification channel read", exception);
        }
    }

    private void addItem(
        UUID userId,
        UUID guildId,
        UUID channelId,
        UUID sourceId,
        long sequence,
        NotificationKind kind,
        String summary
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        if (!preferences(userId).enabled(kind)) {
            return;
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO notification_items(
                     id, user_id, guild_id, channel_id, source_id, sequence, kind, summary, read, created_at
                 )
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, FALSE, ?)
                 ON CONFLICT (user_id, source_id, kind) DO NOTHING
                 """)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, guildId);
            statement.setObject(4, channelId);
            statement.setObject(5, sourceId);
            statement.setLong(6, sequence);
            statement.setString(7, kind.name());
            statement.setString(8, summary);
            statement.setTimestamp(9, Timestamp.from(clock.instant()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to add notification item", exception);
        }
    }

    private NotificationPreferences preferences(UUID userId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT mentions_enabled, direct_messages_enabled, server_notifications_enabled
                 FROM notification_preferences
                 WHERE user_id = ?
                 """)) {
            statement.setObject(1, userId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return NotificationPreferences.defaults();
                }
                return new NotificationPreferences(
                    resultSet.getBoolean("mentions_enabled"),
                    resultSet.getBoolean("direct_messages_enabled"),
                    resultSet.getBoolean("server_notifications_enabled")
                );
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load notification preferences", exception);
        }
    }

    private static NotificationItem item(ResultSet resultSet) throws SQLException {
        return new NotificationItem(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("user_id", UUID.class),
            resultSet.getObject("guild_id", UUID.class),
            resultSet.getObject("channel_id", UUID.class),
            resultSet.getObject("source_id", UUID.class),
            resultSet.getLong("sequence"),
            NotificationKind.valueOf(resultSet.getString("kind")),
            resultSet.getString("summary"),
            resultSet.getBoolean("read"),
            resultSet.getTimestamp("created_at").toInstant()
        );
    }
}
