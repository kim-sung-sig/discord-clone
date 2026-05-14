package com.example.discord.message;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class JdbcMessageSnapshotStore implements MessageSnapshotStore {
    private final DataSource dataSource;

    JdbcMessageSnapshotStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Message> loadAll() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id, guild_id, channel_id, author_id, content, pinned, deleted, edited, created_at, updated_at
                 FROM messages
                 ORDER BY channel_id, sequence
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            List<Message> messages = new ArrayList<>();
            while (resultSet.next()) {
                UUID messageId = resultSet.getObject("id", UUID.class);
                messages.add(new Message(
                    messageId,
                    resultSet.getObject("guild_id", UUID.class),
                    resultSet.getObject("channel_id", UUID.class),
                    resultSet.getObject("author_id", UUID.class),
                    resultSet.getString("content"),
                    loadMentions(connection, messageId),
                    resultSet.getBoolean("pinned"),
                    resultSet.getBoolean("deleted"),
                    loadEdits(connection, messageId),
                    resultSet.getTimestamp("created_at").toInstant(),
                    resultSet.getTimestamp("updated_at").toInstant()
                ));
            }
            return List.copyOf(messages);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to load message snapshots", exception);
        }
    }

    @Override
    public void save(Message message) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertMessage(connection, message);
                replaceMentions(connection, message);
                replaceEdits(connection, message);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save message snapshot", exception);
        }
    }

    private static void upsertMessage(Connection connection, Message message) throws SQLException {
        Long sequence = existingSequence(connection, message.id());
        if (sequence == null) {
            sequence = nextSequence(connection, message.channelId());
            try (var statement = connection.prepareStatement("""
                INSERT INTO messages(id, guild_id, channel_id, author_id, sequence, content, pinned, deleted, edited, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
                bindMessage(statement, message, sequence);
                statement.executeUpdate();
            }
            return;
        }

        try (var statement = connection.prepareStatement("""
            UPDATE messages
            SET content = ?,
                pinned = ?,
                deleted = ?,
                edited = ?,
                updated_at = ?
            WHERE id = ?
            """)) {
            statement.setString(1, message.content());
            statement.setBoolean(2, message.pinned());
            statement.setBoolean(3, message.deleted());
            statement.setBoolean(4, message.edited());
            statement.setTimestamp(5, Timestamp.from(message.updatedAt()));
            statement.setObject(6, message.id());
            statement.executeUpdate();
        }
    }

    private static void bindMessage(
        java.sql.PreparedStatement statement,
        Message message,
        long sequence
    ) throws SQLException {
        statement.setObject(1, message.id());
        statement.setObject(2, message.guildId());
        statement.setObject(3, message.channelId());
        statement.setObject(4, message.authorId());
        statement.setLong(5, sequence);
        statement.setString(6, message.content());
        statement.setBoolean(7, message.pinned());
        statement.setBoolean(8, message.deleted());
        statement.setBoolean(9, message.edited());
        statement.setTimestamp(10, Timestamp.from(message.createdAt()));
        statement.setTimestamp(11, Timestamp.from(message.updatedAt()));
    }

    private static Long existingSequence(Connection connection, UUID messageId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT sequence FROM messages WHERE id = ?")) {
            statement.setObject(1, messageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getLong("sequence");
            }
        }
    }

    private static long nextSequence(Connection connection, UUID channelId) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT COALESCE(MAX(sequence), 0) + 1 AS next_sequence FROM messages WHERE channel_id = ?")) {
            statement.setObject(1, channelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("next_sequence");
            }
        }
    }

    private static List<String> loadMentions(Connection connection, UUID messageId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT mention
            FROM message_mention_tokens
            WHERE message_id = ?
            ORDER BY position
            """)) {
            statement.setObject(1, messageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<String> mentions = new ArrayList<>();
                while (resultSet.next()) {
                    mentions.add(resultSet.getString("mention"));
                }
                return mentions;
            }
        }
    }

    private static List<MessageEdit> loadEdits(Connection connection, UUID messageId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT content, edited_at
            FROM message_edits
            WHERE message_id = ?
            ORDER BY edited_at, id
            """)) {
            statement.setObject(1, messageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MessageEdit> edits = new ArrayList<>();
                while (resultSet.next()) {
                    edits.add(new MessageEdit(
                        resultSet.getString("content"),
                        resultSet.getTimestamp("edited_at").toInstant()
                    ));
                }
                return edits;
            }
        }
    }

    private static void replaceMentions(Connection connection, Message message) throws SQLException {
        deleteByMessageId(connection, "DELETE FROM message_mention_tokens WHERE message_id = ?", message.id());
        int position = 0;
        for (String mention : message.mentions()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO message_mention_tokens(message_id, mention, position)
                VALUES (?, ?, ?)
                """)) {
                statement.setObject(1, message.id());
                statement.setString(2, mention);
                statement.setInt(3, position++);
                statement.executeUpdate();
            }
        }
    }

    private static void replaceEdits(Connection connection, Message message) throws SQLException {
        deleteByMessageId(connection, "DELETE FROM message_edits WHERE message_id = ?", message.id());
        for (MessageEdit edit : message.editHistory()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO message_edits(id, message_id, content, edited_at)
                VALUES (?, ?, ?, ?)
                """)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, message.id());
                statement.setString(3, edit.content());
                statement.setTimestamp(4, Timestamp.from(edit.editedAt()));
                statement.executeUpdate();
            }
        }
    }

    private static void deleteByMessageId(Connection connection, String sql, UUID messageId) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, messageId);
            statement.executeUpdate();
        }
    }
}
