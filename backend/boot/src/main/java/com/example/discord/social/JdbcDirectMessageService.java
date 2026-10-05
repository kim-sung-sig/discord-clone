package com.example.discord.social;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcDirectMessageService {
    private final DataSource dataSource;

    JdbcDirectMessageService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    void block(UUID blockerId, UUID blockedId) {
        requirePair(blockerId, blockedId);
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
            INSERT INTO social_blocks(blocker_id, blocked_id) VALUES (?, ?)
            ON CONFLICT (blocker_id, blocked_id) DO NOTHING
            """)) {
            statement.setObject(1, blockerId);
            statement.setObject(2, blockedId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to block user", exception);
        }
    }

    DirectMessageChannel channel(UUID requesterId, UUID targetUserId) {
        Pair pair = Pair.of(requesterId, targetUserId);
        try (Connection connection = dataSource.getConnection()) {
            requireUser(connection, targetUserId);
            requireNotBlocked(connection, requesterId, targetUserId);
            insertChannel(connection, pair);
            return loadChannel(connection, pair);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to open direct message channel", exception);
        }
    }

    DirectMessageReceipt send(UUID senderId, UUID targetUserId, String content) {
        Pair pair = Pair.of(senderId, targetUserId);
        if (content == null || content.isBlank() || content.length() > 4000) {
            throw new IllegalArgumentException("message content must contain 1 to 4000 characters");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireUser(connection, targetUserId);
                requireNotBlocked(connection, senderId, targetUserId);
                insertChannel(connection, pair);
                long sequence;
                try (var statement = connection.prepareStatement("""
                    UPDATE dm_channels SET last_sequence = last_sequence + 1 WHERE id = ?
                    RETURNING last_sequence
                    """)) {
                    statement.setObject(1, pair.channelId());
                    try (var result = statement.executeQuery()) {
                        result.next();
                        sequence = result.getLong(1);
                    }
                }
                UUID messageId = UUID.randomUUID();
                Instant createdAt;
                try (var statement = connection.prepareStatement("""
                    INSERT INTO dm_messages(id, channel_id, sender_id, sequence, content)
                    VALUES (?, ?, ?, ?, ?) RETURNING created_at
                    """)) {
                    statement.setObject(1, messageId);
                    statement.setObject(2, pair.channelId());
                    statement.setObject(3, senderId);
                    statement.setLong(4, sequence);
                    statement.setString(5, content);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        createdAt = result.getTimestamp(1).toInstant();
                    }
                }
                if (directMessagesEnabled(connection, targetUserId)) {
                    try (var statement = connection.prepareStatement("""
                        INSERT INTO notification_items(
                            id, user_id, guild_id, channel_id, source_id, sequence, kind, summary, read, created_at
                        ) VALUES (?, ?, NULL, ?, ?, ?, 'DM', 'New direct message', FALSE, ?)
                        ON CONFLICT (user_id, source_id, kind) DO NOTHING
                        """)) {
                        statement.setObject(1, UUID.randomUUID());
                        statement.setObject(2, targetUserId);
                        statement.setObject(3, pair.channelId());
                        statement.setObject(4, messageId);
                        statement.setLong(5, sequence);
                        statement.setTimestamp(6, Timestamp.from(createdAt));
                        statement.executeUpdate();
                    }
                }
                connection.commit();
                return new DirectMessageReceipt(messageId, pair.channelId(), senderId, targetUserId, content, createdAt);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to send direct message", exception);
        }
    }

    List<DirectMessage> messages(UUID requesterId, UUID targetUserId, Long beforeSequence, int limit) {
        Pair pair = Pair.of(requesterId, targetUserId);
        if (limit < 1 || limit > 100 || (beforeSequence != null && beforeSequence < 1)) {
            throw new IllegalArgumentException("invalid direct message cursor or limit");
        }
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
            SELECT id, sender_id, sequence, content, created_at FROM dm_messages
            WHERE channel_id = ? AND sequence < ? ORDER BY sequence DESC LIMIT ?
            """)) {
            requireNotBlocked(connection, requesterId, targetUserId);
            statement.setObject(1, pair.channelId());
            statement.setLong(2, beforeSequence == null ? Long.MAX_VALUE : beforeSequence);
            statement.setInt(3, limit);
            try (var result = statement.executeQuery()) {
                List<DirectMessage> messages = new ArrayList<>();
                while (result.next()) {
                    messages.add(new DirectMessage(
                        result.getObject("id", UUID.class), pair.channelId(),
                        result.getObject("sender_id", UUID.class), result.getLong("sequence"),
                        result.getString("content"), result.getTimestamp("created_at").toInstant()
                    ));
                }
                return List.copyOf(messages);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list direct messages", exception);
        }
    }

    long markRead(UUID requesterId, UUID targetUserId, long sequence) {
        Pair pair = Pair.of(requesterId, targetUserId);
        if (sequence < 0) {
            throw new IllegalArgumentException("lastReadSequence must not be negative");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                requireNotBlocked(connection, requesterId, targetUserId);
                long latest = latestSequence(connection, pair.channelId());
                if (sequence > latest) {
                    throw new IllegalArgumentException("lastReadSequence exceeds saved messages");
                }
                long stored;
                try (var statement = connection.prepareStatement("""
                    INSERT INTO dm_read_markers(user_id, channel_id, last_read_sequence) VALUES (?, ?, ?)
                    ON CONFLICT (user_id, channel_id) DO UPDATE
                    SET last_read_sequence = GREATEST(dm_read_markers.last_read_sequence, EXCLUDED.last_read_sequence),
                        updated_at = NOW()
                    RETURNING last_read_sequence
                    """)) {
                    statement.setObject(1, requesterId);
                    statement.setObject(2, pair.channelId());
                    statement.setLong(3, sequence);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        stored = result.getLong(1);
                    }
                }
                try (var statement = connection.prepareStatement("""
                    UPDATE notification_items SET read = TRUE, updated_at = NOW()
                    WHERE user_id = ? AND channel_id = ? AND kind = 'DM' AND sequence <= ? AND read = FALSE
                    """)) {
                    statement.setObject(1, requesterId);
                    statement.setObject(2, pair.channelId());
                    statement.setLong(3, stored);
                    statement.executeUpdate();
                }
                connection.commit();
                return stored;
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save direct message read marker", exception);
        }
    }

    long readSequence(UUID requesterId, UUID targetUserId) {
        Pair pair = Pair.of(requesterId, targetUserId);
        try (Connection connection = dataSource.getConnection(); var statement = connection.prepareStatement("""
            SELECT last_read_sequence FROM dm_read_markers WHERE user_id = ? AND channel_id = ?
            """)) {
            requireNotBlocked(connection, requesterId, targetUserId);
            statement.setObject(1, requesterId);
            statement.setObject(2, pair.channelId());
            try (var result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load direct message read marker", exception);
        }
    }

    private static void requireNotBlocked(Connection connection, UUID first, UUID second) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT 1 FROM social_blocks
            WHERE (blocker_id = ? AND blocked_id = ?) OR (blocker_id = ? AND blocked_id = ?)
            """)) {
            statement.setObject(1, first);
            statement.setObject(2, second);
            statement.setObject(3, second);
            statement.setObject(4, first);
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    throw new SocialPolicyException("blocked users cannot use direct messages");
                }
            }
        }
    }

    private static void requireUser(Connection connection, UUID userId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT 1 FROM users WHERE id = ?")) {
            statement.setObject(1, userId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SocialNotFoundException("direct message recipient not found");
                }
            }
        }
    }

    private static void insertChannel(Connection connection, Pair pair) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO dm_channels(id, first_user_id, second_user_id) VALUES (?, ?, ?)
            ON CONFLICT (first_user_id, second_user_id) DO NOTHING
            """)) {
            statement.setObject(1, pair.channelId());
            statement.setObject(2, pair.first());
            statement.setObject(3, pair.second());
            statement.executeUpdate();
        }
    }

    private static DirectMessageChannel loadChannel(Connection connection, Pair pair) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT created_at FROM dm_channels WHERE id = ?")) {
            statement.setObject(1, pair.channelId());
            try (var result = statement.executeQuery()) {
                result.next();
                return new DirectMessageChannel(pair.channelId(), pair.first(), pair.second(), result.getTimestamp(1).toInstant());
            }
        }
    }

    private static long latestSequence(Connection connection, UUID channelId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT last_sequence FROM dm_channels WHERE id = ?")) {
            statement.setObject(1, channelId);
            try (var result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new SocialNotFoundException("direct message channel not found");
                }
                return result.getLong(1);
            }
        }
    }

    private static boolean directMessagesEnabled(Connection connection, UUID userId) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT direct_messages_enabled FROM notification_preferences WHERE user_id = ?")) {
            statement.setObject(1, userId);
            try (var result = statement.executeQuery()) {
                return !result.next() || result.getBoolean(1);
            }
        }
    }

    private static void requirePair(UUID first, UUID second) {
        if (first == null || second == null || first.equals(second)) {
            throw new IllegalArgumentException("two different users are required");
        }
    }

    private record Pair(UUID first, UUID second) {
        static Pair of(UUID left, UUID right) {
            requirePair(left, right);
            return left.compareTo(right) < 0 ? new Pair(left, right) : new Pair(right, left);
        }

        UUID channelId() {
            return UUID.nameUUIDFromBytes(("dm:" + first + ":" + second).getBytes(StandardCharsets.UTF_8));
        }
    }

    record DirectMessage(UUID id, UUID channelId, UUID senderId, long sequence, String content, Instant createdAt) {
    }
}
