package com.example.discord.authorization;

import com.example.discord.permission.AuthzProjectionUpdated;
import com.example.discord.permission.AuthorizationAudience;
import com.example.discord.permission.AuthorizationProjectionEventEnvelope;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.AuthorizationWatermarkAdvanced;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("postgres & kafka")
class AuthorizationProjectionOutboxRelay {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String topicPrefix;
    private final int batchSize;
    private final Duration claimLease;
    private final Duration publishTimeout;

    AuthorizationProjectionOutboxRelay(
        JdbcTemplate jdbc,
        KafkaTemplate<String, String> kafka,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${discord.kafka.topic-prefix:discord}") String topicPrefix,
        @Value("${discord.authz.outbox-batch-size:50}") int batchSize,
        @Value("${discord.authz.outbox-claim-lease-seconds:30}") long claimLeaseSeconds,
        @Value("${discord.authz.outbox-publish-timeout-ms:5000}") long publishTimeoutMs
    ) {
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.topicPrefix = topicPrefix == null || topicPrefix.isBlank() ? "discord" : topicPrefix.trim();
        this.batchSize = Math.max(1, batchSize);
        this.claimLease = Duration.ofSeconds(Math.max(1, claimLeaseSeconds));
        this.publishTimeout = Duration.ofMillis(Math.max(1, publishTimeoutMs));
    }

    @Scheduled(fixedDelayString = "${discord.authz.outbox-relay-delay-ms:1000}")
    void relayPending() {
        for (OutboxRow row : claimPending(batchSize)) {
            publish(row);
        }
    }

    @Transactional
    List<OutboxRow> claimPending(int limit) {
        Instant claimUntil = clock.instant().plus(claimLease);
        return jdbc.query("""
            WITH candidates AS (
                SELECT candidate.event_id
                FROM authorization_projection_outbox candidate
                WHERE candidate.published_at IS NULL
                  AND (candidate.claimed_until IS NULL OR candidate.claimed_until < now())
                  AND NOT EXISTS (
                      SELECT 1
                      FROM authorization_projection_outbox predecessor
                      WHERE predecessor.guild_id = candidate.guild_id
                        AND predecessor.audience = candidate.audience
                        AND predecessor.published_at IS NULL
                        AND (
                            predecessor.permission_version < candidate.permission_version
                            OR (
                                predecessor.permission_version = candidate.permission_version
                                AND predecessor.event_kind = 'PROJECTION_UPDATED'
                                AND candidate.event_kind = 'WATERMARK_ADVANCED'
                            )
                        )
                  )
                ORDER BY candidate.permission_version,
                         CASE WHEN candidate.event_kind = 'PROJECTION_UPDATED' THEN 0 ELSE 1 END,
                         candidate.occurred_at, candidate.event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
            )
            UPDATE authorization_projection_outbox outbox
            SET claimed_until = ?, claim_token = ?, attempts = attempts + 1
            FROM candidates
            WHERE outbox.event_id = candidates.event_id
            RETURNING outbox.event_id, outbox.guild_id, outbox.event_kind, outbox.subject_id,
                      outbox.resource_type, outbox.resource_id, outbox.permission_bits,
                      outbox.permission_version, outbox.audience, outbox.correlation_id,
                      outbox.occurred_at, outbox.claim_token
            """, AuthorizationProjectionOutboxRelay::rowFrom, limit, Timestamp.from(claimUntil), UUID.randomUUID());
    }

    private void publish(OutboxRow row) {
        try {
            AuthorizationProjectionEventEnvelope envelope = row.envelope();
            String payload = objectMapper.writeValueAsString(envelope);
            kafka.send(topic(envelope.audience()), envelope.guildId().toString(), payload)
                .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            jdbc.update("""
                UPDATE authorization_projection_outbox
                SET published_at = ?, claimed_until = NULL, claim_token = NULL, last_error = NULL
                WHERE event_id = ? AND claim_token = ? AND published_at IS NULL
                """, Timestamp.from(clock.instant()), row.eventId(), row.claimToken());
        } catch (Exception failure) {
            jdbc.update("""
                UPDATE authorization_projection_outbox
                SET claimed_until = NULL, claim_token = NULL, last_error = ?
                WHERE event_id = ? AND claim_token = ? AND published_at IS NULL
                """, safeError(failure), row.eventId(), row.claimToken());
        }
    }

    private String topic(AuthorizationAudience audience) {
        return topicPrefix + ".authz." + audience.name().toLowerCase(java.util.Locale.ROOT) + ".v1";
    }

    private static OutboxRow rowFrom(ResultSet rs, int ignored) throws SQLException {
        return new OutboxRow(
            rs.getObject("event_id", UUID.class), rs.getObject("guild_id", UUID.class), rs.getString("event_kind"),
            rs.getObject("subject_id", UUID.class), enumValue(AuthorizationResourceType.class, rs.getString("resource_type")),
            rs.getObject("resource_id", UUID.class), rs.getObject("permission_bits", Long.class),
            rs.getLong("permission_version"), AuthorizationAudience.valueOf(rs.getString("audience")),
            rs.getObject("correlation_id", UUID.class), rs.getTimestamp("occurred_at").toInstant(),
            rs.getObject("claim_token", UUID.class));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, String value) {
        return value == null ? null : Enum.valueOf(type, value);
    }

    private static String safeError(Exception failure) {
        return failure.getClass().getSimpleName();
    }

    record OutboxRow(
        UUID eventId,
        UUID guildId,
        String eventKind,
        UUID subjectId,
        AuthorizationResourceType resourceType,
        UUID resourceId,
        Long permissionBits,
        long permissionVersion,
        AuthorizationAudience audience,
        UUID correlationId,
        Instant occurredAt,
        UUID claimToken
    ) {
        AuthorizationProjectionEventEnvelope envelope() {
            if (AuthorizationProjectionEventEnvelope.PROJECTION_UPDATED.equals(eventKind)) {
                return AuthorizationProjectionEventEnvelope.projection(new AuthzProjectionUpdated(
                    eventId, guildId, subjectId, resourceType, resourceId, permissionBits, permissionVersion,
                    audience, occurredAt, correlationId));
            }
            return AuthorizationProjectionEventEnvelope.watermark(
                new AuthorizationWatermarkAdvanced(eventId, guildId, permissionVersion, audience), occurredAt);
        }
    }
}
