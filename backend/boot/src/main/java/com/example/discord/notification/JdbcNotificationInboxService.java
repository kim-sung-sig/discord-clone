package com.example.discord.notification;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (UUID userId : command.mentionedUserIds()) {
                    if (userId.equals(command.authorId()) || !command.visibleRecipientIds().contains(userId)) {
                        continue;
                    }
                    addItem(connection, userId, command.guildId(), command.channelId(), command.messageId(),
                        command.sequence(), NotificationKind.MENTION, command.summary());
                }
                connection.commit();
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to record mention notifications", exception);
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

    List<NotificationItem> page(UUID userId, Set<UUID> visibleChannelIds, UUID beforeId, int limit) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(visibleChannelIds, "visibleChannelIds must not be null");
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
        try (Connection connection = dataSource.getConnection()) {
            Timestamp beforeTime = null;
            if (beforeId != null) {
                try (var cursor = connection.prepareStatement(
                    "SELECT created_at FROM notification_items WHERE user_id = ? AND id = ?")) {
                    cursor.setObject(1, userId);
                    cursor.setObject(2, beforeId);
                    try (var result = cursor.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException("notification cursor not found");
                        }
                        beforeTime = result.getTimestamp(1);
                    }
                }
            }
            try (var statement = connection.prepareStatement("""
                SELECT id, user_id, guild_id, channel_id, source_id, sequence, kind, summary, read, created_at
                FROM notification_items
                WHERE user_id = ? AND ((guild_id IS NOT NULL AND channel_id = ANY(?)) OR (kind = 'DM' AND EXISTS (
                    SELECT 1 FROM dm_channels dm
                    WHERE dm.id = notification_items.channel_id
                      AND notification_items.user_id IN (dm.first_user_id, dm.second_user_id)
                      AND NOT EXISTS (
                          SELECT 1 FROM social_blocks block
                          WHERE (block.blocker_id = dm.first_user_id AND block.blocked_id = dm.second_user_id)
                             OR (block.blocker_id = dm.second_user_id AND block.blocked_id = dm.first_user_id)
                      )
                )))
                  AND (?::timestamptz IS NULL OR (created_at, id) < (?::timestamptz, ?::uuid))
                ORDER BY created_at DESC, id DESC LIMIT ?
                """)) {
                statement.setObject(1, userId);
                statement.setArray(2, connection.createArrayOf("uuid", visibleChannelIds.toArray()));
                statement.setTimestamp(3, beforeTime);
                statement.setTimestamp(4, beforeTime);
                statement.setObject(5, beforeId);
                statement.setInt(6, limit);
                try (var result = statement.executeQuery()) {
                    List<NotificationItem> items = new ArrayList<>();
                    while (result.next()) {
                        items.add(item(result));
                    }
                    return List.copyOf(items);
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to page notification inbox", exception);
        }
    }

    Map<UUID, UUID> serverEventIds(List<NotificationItem> items) {
        Object[] signalIds = items.stream()
            .filter(item -> item.kind() == NotificationKind.SERVER)
            .map(NotificationItem::sourceId)
            .toArray();
        if (signalIds.length == 0) {
            return Map.of();
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, event_id FROM server_event_signals WHERE id = ANY(?)
                 """)) {
            statement.setArray(1, connection.createArrayOf("uuid", signalIds));
            try (var result = statement.executeQuery()) {
                Map<UUID, UUID> ids = new HashMap<>();
                while (result.next()) {
                    ids.put(result.getObject(1, UUID.class), result.getObject(2, UUID.class));
                }
                return Map.copyOf(ids);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to resolve server notification targets", exception);
        }
    }

    Map<UUID, UUID> dmSenderIds(List<NotificationItem> items) {
        Object[] messageIds = items.stream()
            .filter(item -> item.kind() == NotificationKind.DM)
            .map(NotificationItem::sourceId)
            .toArray();
        if (messageIds.length == 0) {
            return Map.of();
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT id, sender_id FROM dm_messages WHERE id = ANY(?)")) {
            statement.setArray(1, connection.createArrayOf("uuid", messageIds));
            try (var result = statement.executeQuery()) {
                Map<UUID, UUID> ids = new HashMap<>();
                while (result.next()) {
                    ids.put(result.getObject(1, UUID.class), result.getObject(2, UUID.class));
                }
                return Map.copyOf(ids);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to resolve direct message notification targets", exception);
        }
    }

    long unreadCount(UUID userId, Set<UUID> visibleChannelIds) {
        Objects.requireNonNull(userId, "userId must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT COUNT(*) FROM notification_items
                 WHERE user_id = ? AND read = FALSE
                   AND ((guild_id IS NOT NULL AND channel_id = ANY(?)) OR (kind = 'DM' AND EXISTS (
                       SELECT 1 FROM dm_channels dm
                       WHERE dm.id = notification_items.channel_id
                         AND notification_items.user_id IN (dm.first_user_id, dm.second_user_id)
                         AND NOT EXISTS (
                             SELECT 1 FROM social_blocks block
                             WHERE (block.blocker_id = dm.first_user_id AND block.blocked_id = dm.second_user_id)
                                OR (block.blocker_id = dm.second_user_id AND block.blocked_id = dm.first_user_id)
                         )
                   )))
                 """)) {
            statement.setObject(1, userId);
            statement.setArray(2, connection.createArrayOf("uuid", visibleChannelIds.toArray()));
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count visible notifications", exception);
        }
    }

    Map<UUID, Long> unreadByChannel(UUID userId, Set<UUID> visibleChannelIds) {
        Objects.requireNonNull(userId, "userId must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT message.channel_id, COUNT(*) AS unread_count
                 FROM messages message
                 LEFT JOIN channel_read_markers marker
                   ON marker.channel_id = message.channel_id AND marker.user_id = ?
                 WHERE message.channel_id = ANY(?) AND message.author_id <> ? AND message.deleted = FALSE
                   AND message.sequence > COALESCE(marker.last_read_sequence, 0)
                 GROUP BY message.channel_id
                 """)) {
            statement.setObject(1, userId);
            statement.setArray(2, connection.createArrayOf("uuid", visibleChannelIds.toArray()));
            statement.setObject(3, userId);
            try (var result = statement.executeQuery()) {
                Map<UUID, Long> counts = new HashMap<>();
                while (result.next()) {
                    counts.put(result.getObject("channel_id", UUID.class), result.getLong("unread_count"));
                }
                return Map.copyOf(counts);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count unread channel messages", exception);
        }
    }

    boolean markServerItemRead(UUID userId, UUID itemId, Set<UUID> visibleChannelIds) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE notification_items SET read = TRUE, updated_at = NOW()
                 WHERE id = ? AND user_id = ? AND kind = 'SERVER' AND channel_id = ANY(?)
                 """)) {
            statement.setObject(1, itemId);
            statement.setObject(2, userId);
            statement.setArray(3, connection.createArrayOf("uuid", visibleChannelIds.toArray()));
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark server notification read", exception);
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
        try (Connection connection = dataSource.getConnection()) {
            addItem(connection, userId, guildId, channelId, sourceId, sequence, kind, summary);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to add notification item", exception);
        }
    }

    private void addItem(
        Connection connection, UUID userId, UUID guildId, UUID channelId, UUID sourceId,
        long sequence, NotificationKind kind, String summary
    ) throws SQLException {
        if (!preferences(connection, userId).enabled(kind)) {
            return;
        }
        try (var statement = connection.prepareStatement("""
                 INSERT INTO notification_items(
                     id, user_id, guild_id, channel_id, source_id, sequence, kind, summary, read, created_at
                 )
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? = 'MENTION' THEN EXISTS (
                     SELECT 1 FROM channel_read_markers
                     WHERE user_id = ? AND channel_id = ? AND last_read_sequence >= ?
                 ) ELSE FALSE END, ?)
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
            statement.setString(9, kind.name());
            statement.setObject(10, userId);
            statement.setObject(11, channelId);
            statement.setLong(12, sequence);
            statement.setTimestamp(13, Timestamp.from(clock.instant()));
            statement.executeUpdate();
        }
    }

    private NotificationPreferences preferences(Connection connection, UUID userId) throws SQLException {
        try (var statement = connection.prepareStatement("""
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
