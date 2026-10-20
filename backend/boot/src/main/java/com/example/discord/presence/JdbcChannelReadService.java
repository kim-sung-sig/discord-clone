package com.example.discord.presence;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcChannelReadService {
    private final DataSource dataSource;

    JdbcChannelReadService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    ReadMarker markRead(UUID channelId, UUID userId, long sequence) {
        if (sequence < 0) {
            throw new IllegalArgumentException("lastReadSequence must not be negative");
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long latest = latestSequence(connection, channelId);
                if (sequence > latest) {
                    throw new IllegalArgumentException("lastReadSequence exceeds saved messages");
                }
                try (var statement = connection.prepareStatement("""
                    INSERT INTO channel_read_markers(user_id, channel_id, last_read_sequence)
                    VALUES (?, ?, ?)
                    ON CONFLICT (user_id, channel_id) DO UPDATE
                    SET last_read_sequence = GREATEST(channel_read_markers.last_read_sequence, EXCLUDED.last_read_sequence),
                        updated_at = NOW()
                    RETURNING last_read_sequence, updated_at
                    """)) {
                    statement.setObject(1, userId);
                    statement.setObject(2, channelId);
                    statement.setLong(3, sequence);
                    try (var result = statement.executeQuery()) {
                        result.next();
                        ReadMarker marker = new ReadMarker(channelId, userId, result.getLong(1), result.getTimestamp(2).toInstant());
                        try (var update = connection.prepareStatement("""
                            UPDATE notification_items SET read = TRUE, updated_at = NOW()
                            WHERE user_id = ? AND channel_id = ? AND kind = 'MENTION' AND sequence <= ? AND read = FALSE
                            """)) {
                            update.setObject(1, userId);
                            update.setObject(2, channelId);
                            update.setLong(3, marker.lastReadSequence());
                            update.executeUpdate();
                        }
                        connection.commit();
                        return marker;
                    }
                }
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save channel read marker", exception);
        }
    }

    ReadMarker readMarker(UUID channelId, UUID userId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT last_read_sequence, updated_at FROM channel_read_markers
                 WHERE user_id = ? AND channel_id = ?
                 """)) {
            statement.setObject(1, userId);
            statement.setObject(2, channelId);
            try (var result = statement.executeQuery()) {
                if (result.next()) {
                    return new ReadMarker(channelId, userId, result.getLong(1), result.getTimestamp(2).toInstant());
                }
            }
            return new ReadMarker(channelId, userId, 0, Instant.EPOCH);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read channel marker", exception);
        }
    }

    long unreadCount(UUID channelId, UUID userId) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT COUNT(*) FROM messages m
                 WHERE m.channel_id = ? AND m.author_id <> ? AND m.deleted = FALSE
                   AND m.sequence > COALESCE((
                       SELECT last_read_sequence FROM channel_read_markers
                       WHERE user_id = ? AND channel_id = ?
                   ), 0)
                 """)) {
            statement.setObject(1, channelId);
            statement.setObject(2, userId);
            statement.setObject(3, userId);
            statement.setObject(4, channelId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count unread messages", exception);
        }
    }

    private static long latestSequence(Connection connection, UUID channelId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COALESCE(MAX(sequence), 0) FROM messages WHERE channel_id = ?")) {
            statement.setObject(1, channelId);
            try (var result = statement.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }
}
