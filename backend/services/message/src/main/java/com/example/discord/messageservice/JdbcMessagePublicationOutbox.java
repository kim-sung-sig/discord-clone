package com.example.discord.messageservice;

import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.ClaimedMessagePublication;
import com.example.discord.message.MessagePublished;
import com.example.discord.message.MessagePublicationOutboxQueue;
import com.example.discord.message.UserMessageAuthor;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres & kafka")
class JdbcMessagePublicationOutbox implements MessagePublicationOutboxQueue {
    private final JdbcTemplate jdbc;

    JdbcMessagePublicationOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<ClaimedMessagePublication> claimPendingPublications(int limit, Instant claimedAt, Duration lease) {
        UUID claimToken = UUID.randomUUID();
        return jdbc.query("""
            WITH candidates AS (
                SELECT event_id FROM message_publication_outbox
                WHERE published_at IS NULL AND dead_lettered_at IS NULL
                  AND (claim_expires_at IS NULL OR claim_expires_at <= ?)
                ORDER BY occurred_at, event_id
                LIMIT ? FOR UPDATE SKIP LOCKED
            )
            UPDATE message_publication_outbox outbox
            SET claim_token=?, claimed_at=?, claim_expires_at=?
            FROM candidates WHERE outbox.event_id=candidates.event_id
            RETURNING outbox.event_id,outbox.message_id,outbox.author_id,
                      outbox.guild_id,outbox.channel_id,outbox.correlation_id,
                      outbox.occurred_at,outbox.claim_token
            """,
            (ResultSet rs, int ignored) -> new ClaimedMessagePublication(
                new MessagePublished(
                    rs.getObject("event_id", UUID.class),
                    rs.getObject("message_id", UUID.class),
                    new UserMessageAuthor(rs.getObject("author_id", UUID.class)),
                    new ChannelMessageTarget(
                        rs.getObject("guild_id", UUID.class),
                        rs.getObject("channel_id", UUID.class)
                    ),
                    List.of(),
                    rs.getString("correlation_id"),
                    rs.getTimestamp("occurred_at").toInstant()
                ),
                rs.getObject("claim_token", UUID.class)
            ),
            Timestamp.from(claimedAt),
            Math.max(1, Math.min(limit, 100)),
            claimToken,
            Timestamp.from(claimedAt),
            Timestamp.from(claimedAt.plus(lease))
        );
    }

    @Override
    public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
        jdbc.update("""
            UPDATE message_publication_outbox
            SET published_at=?, claim_token=NULL, claimed_at=NULL, claim_expires_at=NULL
            WHERE event_id=? AND claim_token=? AND published_at IS NULL
            """, Timestamp.from(publishedAt), eventId, claimToken);
    }

    @Override
    public void releaseFailed(UUID eventId, UUID claimToken, String errorMessage, Instant failedAt, Duration retryDelay) {
        jdbc.update("""
            UPDATE message_publication_outbox
            SET attempts=attempts+1,
                last_error=?,
                claim_token=NULL,
                claimed_at=NULL,
                claim_expires_at=CASE WHEN attempts + 1 >= 10 THEN NULL ELSE CAST(? AS TIMESTAMPTZ) END,
                dead_lettered_at=CASE WHEN attempts + 1 >= 10 THEN CAST(? AS TIMESTAMPTZ) ELSE NULL END
            WHERE event_id=? AND claim_token=? AND published_at IS NULL
            """,
            safeError(errorMessage),
            Timestamp.from(failedAt.plus(retryDelay)),
            Timestamp.from(failedAt),
            eventId,
            claimToken
        );
    }

    @Override
    public long unpublishedBacklogCount() {
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM message_publication_outbox WHERE published_at IS NULL AND dead_lettered_at IS NULL",
            Long.class
        );
        return count == null ? 0L : count;
    }

    private static String safeError(String errorMessage) {
        if (errorMessage == null || errorMessage.isBlank()) return "dispatch failed";
        return errorMessage.length() <= 256 ? errorMessage : errorMessage.substring(0, 256);
    }
}
