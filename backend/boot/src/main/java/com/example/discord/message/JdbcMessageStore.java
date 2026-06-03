package com.example.discord.message;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcMessageStore implements
    MessageStore,
    MessagePublicationStore,
    ChannelMessagePagePort,
    ChannelMessageSearchPort,
    MessageLookupPort,
    MessagePublicationOutbox,
    MessagePublicationOutboxQueue,
    MessagePublicationDeadLetterQueue {
    private static final Duration IDEMPOTENCY_RETENTION = Duration.ofDays(7);
    private static final int MAX_PUBLICATION_ATTEMPTS = 10;

    private final DataSource dataSource;

    JdbcMessageStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
    }

    @Override
    public Optional<Message> findById(UUID messageId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        try (Connection connection = dataSource.getConnection()) {
            return findById(connection, messageId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find message", exception);
        }
    }

    @Override
    public Optional<Message> findByIdempotencyKey(
        MessageAuthor author,
        MessageTarget target,
        IdempotencyKey idempotencyKey
    ) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Optional<MessageScope> scope = MessageScope.from(author, target);
        if (scope.isEmpty()) {
            return Optional.empty();
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT message_id
                 FROM message_idempotency_keys
                 WHERE author_type = ?
                   AND author_id = ?
                   AND target_type = ?
                   AND guild_id = ?
                   AND channel_id = ?
                   AND idempotency_key = ?
                   AND expires_at > ?
                 """)) {
            bindScope(statement, scope.get());
            statement.setString(6, idempotencyKey.value());
            statement.setTimestamp(7, Timestamp.from(Instant.now()));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return findById(connection, resultSet.getObject("message_id", UUID.class));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find idempotent message", exception);
        }
    }

    @Override
    public Message save(Message message) {
        Objects.requireNonNull(message, "message must not be null");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                upsertMessage(connection, message);
                replaceMentions(connection, message);
                replaceEdits(connection, message);
                connection.commit();
                return message;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save message", exception);
        }
    }

    @Override
    public Message save(Message message, IdempotencyKey idempotencyKey) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                cleanupExpiredIdempotencyKeys(connection);
                upsertMessage(connection, message);
                replaceMentions(connection, message);
                replaceEdits(connection, message);
                insertIdempotencyKey(connection, message, idempotencyKey);
                connection.commit();
                return message;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save message with idempotency key", exception);
        }
    }

    @Override
    public Message savePublished(
        Message message,
        IdempotencyKey idempotencyKey,
        MessagePublished event
    ) {
        Objects.requireNonNull(message, "message must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(event, "event must not be null");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                cleanupExpiredIdempotencyKeys(connection);
                upsertMessage(connection, message);
                replaceMentions(connection, message);
                replaceEdits(connection, message);
                insertIdempotencyKey(connection, message, idempotencyKey);
                MessageScope scope = MessageScope.from(event.author(), event.target())
                    .orElseThrow(() -> new IllegalArgumentException("unsupported message publication scope"));
                insertOutboxEvent(connection, event, scope);
                insertOutboxMentions(connection, event);
                connection.commit();
                return message;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to save published message", exception);
        }
    }

    @Override
    public MessagePage read(ChannelMessageTarget target, String beforeCursor, int limit) {
        Objects.requireNonNull(target, "target must not be null");
        int pageSize = pageSize(limit);
        Cursor before = beforeCursor == null || beforeCursor.isBlank() ? null : Cursor.decode(beforeCursor);
        try (Connection connection = dataSource.getConnection()) {
            List<Message> messages = readPage(connection, target, before, pageSize + 1);
            boolean hasMore = messages.size() > pageSize;
            List<Message> visiblePage = hasMore ? messages.subList(0, pageSize) : messages;
            String nextCursor = hasMore ? Cursor.from(visiblePage.getLast()).encode() : null;
            return new MessagePage(visiblePage, nextCursor);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read channel messages", exception);
        }
    }

    @Override
    public List<Message> search(ChannelMessageTarget target, String query, int limit) {
        Objects.requireNonNull(target, "target must not be null");
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.of();
        }
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT id
                 FROM messages
                 WHERE guild_id = ?
                   AND channel_id = ?
                   AND deleted = false
                   AND LOWER(content) LIKE ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT ?
                 """)) {
            statement.setObject(1, target.guildId());
            statement.setObject(2, target.channelId());
            statement.setString(3, "%" + normalized + "%");
            statement.setInt(4, pageSize(limit));
            return findMessages(connection, statement);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to search channel messages", exception);
        }
    }

    @Override
    public Message requireMessage(ChannelMessageTarget target, UUID messageId) {
        Message message = findById(messageId).orElseThrow(MessageNotFoundException::new);
        if (!message.guildId().equals(target.guildId()) || !message.channelId().equals(target.channelId())) {
            throw new MessageNotFoundException();
        }
        return message;
    }

    @Override
    public void append(MessagePublished event) {
        Objects.requireNonNull(event, "event must not be null");
        MessageScope scope = MessageScope.from(event.author(), event.target())
            .orElseThrow(() -> new IllegalArgumentException("unsupported message publication scope"));
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                insertOutboxEvent(connection, event, scope);
                insertOutboxMentions(connection, event);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to append message publication outbox event", exception);
        }
    }

    @Override
    public List<ClaimedMessagePublication> claimPendingPublications(
        int limit,
        Instant claimedAt,
        Duration lease
    ) {
        Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
        UUID claimToken = UUID.randomUUID();
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 WITH candidates AS (
                     SELECT event_id
                     FROM message_publication_outbox
                     WHERE published_at IS NULL
                       AND dead_lettered_at IS NULL
                       AND (claim_expires_at IS NULL OR claim_expires_at <= ?)
                     ORDER BY occurred_at, event_id
                     LIMIT ?
                     FOR UPDATE SKIP LOCKED
                 )
                 UPDATE message_publication_outbox outbox
                 SET claim_token = ?,
                     claimed_at = ?,
                     claim_expires_at = ?
                 FROM candidates
                 WHERE outbox.event_id = candidates.event_id
                 RETURNING outbox.event_id,
                           outbox.message_id,
                           outbox.author_type,
                           outbox.author_id,
                           outbox.target_type,
                           outbox.guild_id,
                           outbox.channel_id,
                           outbox.correlation_id,
                           outbox.occurred_at,
                           outbox.claim_token
                 """)) {
            statement.setTimestamp(1, Timestamp.from(claimedAt));
            statement.setInt(2, pageSize(limit));
            statement.setObject(3, claimToken);
            statement.setTimestamp(4, Timestamp.from(claimedAt));
            statement.setTimestamp(5, Timestamp.from(claimedAt.plus(lease)));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<ClaimedMessagePublication> publications = new ArrayList<>();
                while (resultSet.next()) {
                    publications.add(new ClaimedMessagePublication(
                        outboxEventFrom(connection, resultSet),
                        resultSet.getObject("claim_token", UUID.class)
                    ));
                }
                return List.copyOf(publications);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to claim message publication outbox events", exception);
        }
    }

    @Override
    public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE message_publication_outbox
                 SET published_at = ?,
                     claim_token = NULL,
                     claimed_at = NULL,
                     claim_expires_at = NULL
                 WHERE event_id = ?
                   AND claim_token = ?
                   AND published_at IS NULL
                 """)) {
            statement.setTimestamp(1, Timestamp.from(publishedAt));
            statement.setObject(2, eventId);
            statement.setObject(3, claimToken);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark message publication outbox event published", exception);
        }
    }

    @Override
    public void releaseFailed(UUID eventId, UUID claimToken, String errorMessage, Instant failedAt) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE message_publication_outbox
                 SET attempts = attempts + 1,
                     last_error = ?,
                     dead_lettered_at = CASE
                         WHEN attempts + 1 >= ? THEN ?
                         ELSE dead_lettered_at
                     END,
                     claim_token = NULL,
                     claimed_at = NULL,
                     claim_expires_at = NULL
                 WHERE event_id = ?
                   AND claim_token = ?
                   AND published_at IS NULL
                 """)) {
            statement.setString(1, errorMessage == null ? "" : errorMessage);
            statement.setInt(2, MAX_PUBLICATION_ATTEMPTS);
            statement.setTimestamp(3, Timestamp.from(failedAt));
            statement.setObject(4, eventId);
            statement.setObject(5, claimToken);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to release failed message publication outbox event", exception);
        }
    }

    @Override
    public List<DeadLetteredMessagePublication> listDeadLetters(int limit) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT event_id,
                        message_id,
                        author_type,
                        author_id,
                        target_type,
                        guild_id,
                        channel_id,
                        correlation_id,
                        occurred_at,
                        attempts,
                        last_error,
                        dead_lettered_at
                 FROM message_publication_outbox
                 WHERE published_at IS NULL
                   AND dead_lettered_at IS NOT NULL
                 ORDER BY dead_lettered_at, event_id
                 LIMIT ?
                 """)) {
            statement.setInt(1, pageSize(limit));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DeadLetteredMessagePublication> deadLetters = new ArrayList<>();
                while (resultSet.next()) {
                    deadLetters.add(new DeadLetteredMessagePublication(
                        outboxEventFrom(connection, resultSet),
                        resultSet.getInt("attempts"),
                        resultSet.getString("last_error"),
                        resultSet.getTimestamp("dead_lettered_at").toInstant()
                    ));
                }
                return List.copyOf(deadLetters);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to list message publication dead letters", exception);
        }
    }

    @Override
    public boolean requeueDeadLetter(UUID eventId, Instant requestedAt) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 UPDATE message_publication_outbox
                 SET dead_lettered_at = NULL,
                     attempts = 0,
                     last_error = NULL,
                     claim_token = NULL,
                     claimed_at = NULL,
                     claim_expires_at = NULL
                 WHERE event_id = ?
                   AND published_at IS NULL
                   AND dead_lettered_at IS NOT NULL
                 """)) {
            statement.setObject(1, eventId);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to requeue message publication dead letter", exception);
        }
    }

    private static Optional<Message> findById(Connection connection, UUID messageId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT id, guild_id, channel_id, author_id, content, pinned, deleted, edited, created_at, updated_at
            FROM messages
            WHERE id = ?
            """)) {
            statement.setObject(1, messageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }
                return Optional.of(messageFrom(connection, resultSet));
            }
        }
    }

    private static Message messageFrom(Connection connection, ResultSet resultSet) throws SQLException {
        UUID messageId = resultSet.getObject("id", UUID.class);
        return new Message(
            messageId,
            new UserMessageAuthor(resultSet.getObject("author_id", UUID.class)),
            new ChannelMessageTarget(
                resultSet.getObject("guild_id", UUID.class),
                resultSet.getObject("channel_id", UUID.class)
            ),
            new MessageContent(resultSet.getString("content")),
            loadMentions(connection, messageId),
            resultSet.getBoolean("pinned"),
            resultSet.getBoolean("deleted"),
            loadEdits(connection, messageId),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant()
        );
    }

    private static List<Message> readPage(
        Connection connection,
        ChannelMessageTarget target,
        Cursor before,
        int limit
    ) throws SQLException {
        if (before == null) {
            try (var statement = connection.prepareStatement("""
                SELECT id
                FROM messages
                WHERE guild_id = ?
                  AND channel_id = ?
                ORDER BY created_at DESC, id DESC
                LIMIT ?
                """)) {
                statement.setObject(1, target.guildId());
                statement.setObject(2, target.channelId());
                statement.setInt(3, limit);
                return findMessages(connection, statement);
            }
        }

        try (var statement = connection.prepareStatement("""
            SELECT id
            FROM messages
            WHERE guild_id = ?
              AND channel_id = ?
              AND (created_at < ? OR (created_at = ? AND id::text < ?))
            ORDER BY created_at DESC, id DESC
            LIMIT ?
            """)) {
            statement.setObject(1, target.guildId());
            statement.setObject(2, target.channelId());
            statement.setTimestamp(3, Timestamp.from(before.createdAt()));
            statement.setTimestamp(4, Timestamp.from(before.createdAt()));
            statement.setString(5, before.id());
            statement.setInt(6, limit);
            return findMessages(connection, statement);
        }
    }

    private static List<Message> findMessages(
        Connection connection,
        java.sql.PreparedStatement statement
    ) throws SQLException {
        try (ResultSet resultSet = statement.executeQuery()) {
            List<Message> messages = new ArrayList<>();
            while (resultSet.next()) {
                findById(connection, resultSet.getObject("id", UUID.class)).ifPresent(messages::add);
            }
            return List.copyOf(messages);
        }
    }

    private static void upsertMessage(Connection connection, Message message) throws SQLException {
        Long sequence = existingSequence(connection, message.id());
        if (sequence == null) {
            insertMessage(connection, message, nextSequence(connection, message.channelId()));
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
            statement.setString(1, message.content().value());
            statement.setBoolean(2, message.pinned());
            statement.setBoolean(3, message.deleted());
            statement.setBoolean(4, message.edited());
            statement.setTimestamp(5, Timestamp.from(message.updatedAt()));
            statement.setObject(6, message.id());
            statement.executeUpdate();
        }
    }

    private static void insertMessage(Connection connection, Message message, long sequence) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO messages(
                id, guild_id, channel_id, author_id, sequence, content, pinned, deleted, edited, created_at, updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setObject(1, message.id());
            statement.setObject(2, message.guildId());
            statement.setObject(3, message.channelId());
            statement.setObject(4, message.authorId());
            statement.setLong(5, sequence);
            statement.setString(6, message.content().value());
            statement.setBoolean(7, message.pinned());
            statement.setBoolean(8, message.deleted());
            statement.setBoolean(9, message.edited());
            statement.setTimestamp(10, Timestamp.from(message.createdAt()));
            statement.setTimestamp(11, Timestamp.from(message.updatedAt()));
            statement.executeUpdate();
        }
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
        try (var statement = connection.prepareStatement("""
            SELECT COALESCE(MAX(sequence), 0) + 1 AS next_sequence
            FROM messages
            WHERE channel_id = ?
            """)) {
            statement.setObject(1, channelId);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong("next_sequence");
            }
        }
    }

    private static void replaceMentions(Connection connection, Message message) throws SQLException {
        deleteByMessageId(connection, "DELETE FROM message_mention_targets WHERE message_id = ?", message.id());
        int position = 0;
        for (MessageMentionTarget mention : message.mentions()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO message_mention_targets(message_id, position, mention_type, target_id, special_kind)
                VALUES (?, ?, ?, ?, ?)
                """)) {
                statement.setObject(1, message.id());
                statement.setInt(2, position++);
                bindMention(statement, 3, mention);
                statement.executeUpdate();
            }
        }
    }

    private static List<MessageMentionTarget> loadMentions(Connection connection, UUID messageId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT mention_type, target_id, special_kind
            FROM message_mention_targets
            WHERE message_id = ?
            ORDER BY position
            """)) {
            statement.setObject(1, messageId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MessageMentionTarget> mentions = new ArrayList<>();
                while (resultSet.next()) {
                    mentions.add(mentionFrom(resultSet));
                }
                return List.copyOf(mentions);
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
                statement.setString(3, edit.content().value());
                statement.setTimestamp(4, Timestamp.from(edit.editedAt()));
                statement.executeUpdate();
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
                        new MessageContent(resultSet.getString("content")),
                        resultSet.getTimestamp("edited_at").toInstant()
                    ));
                }
                return List.copyOf(edits);
            }
        }
    }

    private static void insertIdempotencyKey(
        Connection connection,
        Message message,
        IdempotencyKey idempotencyKey
    ) throws SQLException {
        MessageScope scope = MessageScope.from(message.author(), message.target())
            .orElseThrow(() -> new IllegalArgumentException("unsupported message idempotency scope"));
        Instant now = Instant.now();
        try (var statement = connection.prepareStatement("""
            INSERT INTO message_idempotency_keys(
                author_type, author_id, target_type, guild_id, channel_id,
                idempotency_key, message_id, created_at, expires_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            bindScope(statement, scope);
            statement.setString(6, idempotencyKey.value());
            statement.setObject(7, message.id());
            statement.setTimestamp(8, Timestamp.from(now));
            statement.setTimestamp(9, Timestamp.from(now.plus(IDEMPOTENCY_RETENTION)));
            statement.executeUpdate();
        }
    }

    private static void cleanupExpiredIdempotencyKeys(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("DELETE FROM message_idempotency_keys WHERE expires_at <= ?")) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        }
    }

    private static void insertOutboxEvent(
        Connection connection,
        MessagePublished event,
        MessageScope scope
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
            INSERT INTO message_publication_outbox(
                event_id, event_type, message_id, author_type, author_id, target_type,
                guild_id, channel_id, correlation_id, occurred_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """)) {
            statement.setObject(1, event.eventId());
            statement.setString(2, "MessagePublished");
            statement.setObject(3, event.messageId());
            statement.setString(4, scope.authorType());
            statement.setObject(5, scope.authorId());
            statement.setString(6, scope.targetType());
            statement.setObject(7, scope.guildId());
            statement.setObject(8, scope.channelId());
            statement.setString(9, event.correlationId());
            statement.setTimestamp(10, Timestamp.from(event.occurredAt()));
            statement.executeUpdate();
        }
    }

    private static void insertOutboxMentions(Connection connection, MessagePublished event) throws SQLException {
        int position = 0;
        for (MessageMentionTarget mention : event.mentions()) {
            try (var statement = connection.prepareStatement("""
                INSERT INTO message_publication_outbox_mentions(
                    event_id, position, mention_type, target_id, special_kind
                )
                VALUES (?, ?, ?, ?, ?)
                """)) {
                statement.setObject(1, event.eventId());
                statement.setInt(2, position++);
                bindMention(statement, 3, mention);
                statement.executeUpdate();
            }
        }
    }

    private static MessagePublished outboxEventFrom(Connection connection, ResultSet resultSet) throws SQLException {
        UUID eventId = resultSet.getObject("event_id", UUID.class);
        return new MessagePublished(
            eventId,
            resultSet.getObject("message_id", UUID.class),
            authorFrom(resultSet),
            targetFrom(resultSet),
            loadOutboxMentions(connection, eventId),
            resultSet.getString("correlation_id"),
            resultSet.getTimestamp("occurred_at").toInstant()
        );
    }

    private static MessageAuthor authorFrom(ResultSet resultSet) throws SQLException {
        return switch (resultSet.getString("author_type")) {
            case "USER" -> new UserMessageAuthor(resultSet.getObject("author_id", UUID.class));
            default -> throw new IllegalStateException("unsupported outbox author type");
        };
    }

    private static MessageTarget targetFrom(ResultSet resultSet) throws SQLException {
        return switch (resultSet.getString("target_type")) {
            case "CHANNEL" -> new ChannelMessageTarget(
                resultSet.getObject("guild_id", UUID.class),
                resultSet.getObject("channel_id", UUID.class)
            );
            default -> throw new IllegalStateException("unsupported outbox target type");
        };
    }

    private static List<MessageMentionTarget> loadOutboxMentions(
        Connection connection,
        UUID eventId
    ) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT mention_type, target_id, special_kind
            FROM message_publication_outbox_mentions
            WHERE event_id = ?
            ORDER BY position
            """)) {
            statement.setObject(1, eventId);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<MessageMentionTarget> mentions = new ArrayList<>();
                while (resultSet.next()) {
                    mentions.add(mentionFrom(resultSet));
                }
                return List.copyOf(mentions);
            }
        }
    }

    private static void bindScope(java.sql.PreparedStatement statement, MessageScope scope) throws SQLException {
        statement.setString(1, scope.authorType());
        statement.setObject(2, scope.authorId());
        statement.setString(3, scope.targetType());
        statement.setObject(4, scope.guildId());
        statement.setObject(5, scope.channelId());
    }

    private static void bindMention(
        java.sql.PreparedStatement statement,
        int startIndex,
        MessageMentionTarget mention
    ) throws SQLException {
        switch (mention) {
            case UserMentionTarget user -> {
                statement.setString(startIndex, "USER");
                statement.setObject(startIndex + 1, user.userId());
                statement.setString(startIndex + 2, null);
            }
            case RoleMentionTarget role -> {
                statement.setString(startIndex, "ROLE");
                statement.setObject(startIndex + 1, role.roleId());
                statement.setString(startIndex + 2, null);
            }
            case ChannelMentionTarget channel -> {
                statement.setString(startIndex, "CHANNEL");
                statement.setObject(startIndex + 1, channel.channelId());
                statement.setString(startIndex + 2, null);
            }
            case SpecialMentionTarget special -> {
                statement.setString(startIndex, "SPECIAL");
                statement.setObject(startIndex + 1, null);
                statement.setString(startIndex + 2, special.kind().name());
            }
        }
    }

    private static MessageMentionTarget mentionFrom(ResultSet resultSet) throws SQLException {
        return switch (resultSet.getString("mention_type")) {
            case "USER" -> new UserMentionTarget(resultSet.getObject("target_id", UUID.class));
            case "ROLE" -> new RoleMentionTarget(resultSet.getObject("target_id", UUID.class));
            case "CHANNEL" -> new ChannelMentionTarget(resultSet.getObject("target_id", UUID.class));
            case "SPECIAL" -> new SpecialMentionTarget(
                SpecialMentionKind.valueOf(resultSet.getString("special_kind"))
            );
            default -> throw new IllegalStateException("unsupported mention type");
        };
    }

    private static void deleteByMessageId(Connection connection, String sql, UUID messageId) throws SQLException {
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, messageId);
            statement.executeUpdate();
        }
    }

    private static int pageSize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }

    private record MessageScope(
        String authorType,
        UUID authorId,
        String targetType,
        UUID guildId,
        UUID channelId
    ) {
        static Optional<MessageScope> from(MessageAuthor author, MessageTarget target) {
            if (author instanceof UserMessageAuthor user && target instanceof ChannelMessageTarget channel) {
                return Optional.of(new MessageScope(
                    "USER",
                    user.userId(),
                    "CHANNEL",
                    channel.guildId(),
                    channel.channelId()
                ));
            }
            return Optional.empty();
        }
    }

    private record Cursor(Instant createdAt, String id) {
        static Cursor from(Message message) {
            return new Cursor(message.createdAt(), message.id().toString());
        }

        static Cursor decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new Cursor(Instant.parse(parts[0]), parts[1]);
        }

        String encode() {
            String value = createdAt + "|" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
