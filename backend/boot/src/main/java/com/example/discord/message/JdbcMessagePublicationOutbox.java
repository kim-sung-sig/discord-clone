package com.example.discord.message;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
class JdbcMessagePublicationOutbox implements MessagePublicationOutboxQueue, MessagePublicationDeadLetterQueue {
    private static final Logger log = LoggerFactory.getLogger(JdbcMessagePublicationOutbox.class);
    private static final int MAX_PUBLICATION_ATTEMPTS = 10;

    private final DataSource dataSource;

    JdbcMessagePublicationOutbox(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
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
                log.info(
                    "Message publication outbox events claimed requestedLimit={} claimedCount={} leaseSeconds={}",
                    limit,
                    publications.size(),
                    lease.toSeconds()
                );
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
            int updated = statement.executeUpdate();
            log.info("Message publication outbox event marked published eventId={} updatedRows={}", eventId, updated);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to mark message publication outbox event published", exception);
        }
    }

    @Override
    public void releaseFailed(
        UUID eventId,
        UUID claimToken,
        String errorMessage,
        Instant failedAt,
        Duration retryDelay
    ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        Objects.requireNonNull(retryDelay, "retryDelay must not be null");
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
                     claim_expires_at = CASE
                         WHEN attempts + 1 >= ? THEN NULL::TIMESTAMPTZ
                         ELSE ?::TIMESTAMPTZ
                     END
                 WHERE event_id = ?
                   AND claim_token = ?
                   AND published_at IS NULL
                 """)) {
            statement.setString(1, errorMessage == null ? "" : errorMessage);
            statement.setInt(2, MAX_PUBLICATION_ATTEMPTS);
            statement.setTimestamp(3, Timestamp.from(failedAt));
            statement.setInt(4, MAX_PUBLICATION_ATTEMPTS);
            statement.setTimestamp(5, Timestamp.from(failedAt.plus(retryDelay)));
            statement.setObject(6, eventId);
            statement.setObject(7, claimToken);
            int updated = statement.executeUpdate();
            log.info(
                "Message publication outbox event released after failure eventId={} updatedRows={} retryDelaySeconds={}",
                eventId,
                updated,
                retryDelay.toSeconds()
            );
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to release failed message publication outbox event", exception);
        }
    }

    @Override
    public long unpublishedBacklogCount() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT COUNT(*) AS backlog_count
                 FROM message_publication_outbox
                 WHERE published_at IS NULL
                   AND dead_lettered_at IS NULL
                 """);
             ResultSet resultSet = statement.executeQuery()) {
            resultSet.next();
            return resultSet.getLong("backlog_count");
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to count unpublished message publication outbox events", exception);
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
            int updated = statement.executeUpdate();
            log.info("Message publication dead letter requeued eventId={} updatedRows={}", eventId, updated);
            return updated > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to requeue message publication dead letter", exception);
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

    private static int pageSize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }
}
