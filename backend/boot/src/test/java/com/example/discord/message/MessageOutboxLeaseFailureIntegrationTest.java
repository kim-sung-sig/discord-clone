package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS", matches = "true")
class MessageOutboxLeaseFailureIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-10-22T00:00:00Z");
    private static final Duration LEASE = Duration.ofSeconds(30);
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");
    private static JdbcTemplate jdbc;
    private static JdbcMessagePublicationOutbox queue;
    private UUID eventId;

    @BeforeAll
    static void migrateOwnedDatabase() {
        var dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        queue = new JdbcMessagePublicationOutbox(dataSource);
    }

    @BeforeEach
    void seedOnePublication() {
        // This database belongs exclusively to this Testcontainers test class.
        jdbc.update("DELETE FROM message_publication_outbox");
        UUID userId = UUID.randomUUID();
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        eventId = UUID.randomUUID();
        Timestamp now = Timestamp.from(NOW);
        jdbc.update("INSERT INTO users(id, username, display_name, created_at, updated_at) VALUES (?,?,?,?,?)",
            userId, userId.toString().replace("-", ""), "Lease", now, now);
        jdbc.update("INSERT INTO guilds(id, name, owner_id) VALUES (?,?,?)", guildId, "Lease", userId);
        jdbc.update("INSERT INTO channels(id,guild_id,name,type) VALUES (?,?,?,?)", channelId, guildId, "lease", "GUILD_TEXT");
        var store = new JdbcMessageStore(jdbc.getDataSource());
        var message = new Message(messageId, new UserMessageAuthor(userId), new ChannelMessageTarget(guildId, channelId),
            new MessageContent("lease"), List.of(), false, false, List.of(), NOW, NOW);
        store.savePublished(message, new IdempotencyKey(UUID.randomUUID().toString()),
            new MessagePublished(eventId, messageId, message.author(), message.target(), List.of(), "lease", NOW));
    }

    @Test
    void leaseCannotBeReclaimedOneMicrosecondBeforeExpiry() {
        queue.claimPendingPublications(1, NOW, LEASE);

        assertThat(queue.claimPendingPublications(1, NOW.plus(LEASE).minusNanos(1000), LEASE)).isEmpty();
    }

    @Test
    void leaseCanBeReclaimedExactlyAtExpiry() {
        var first = queue.claimPendingPublications(1, NOW, LEASE).getFirst();

        var second = queue.claimPendingPublications(1, NOW.plus(LEASE), LEASE).getFirst();

        assertThat(second.event().eventId()).isEqualTo(eventId);
        assertThat(second.claimToken()).isNotEqualTo(first.claimToken());
    }

    @Test
    void expiredOwnerCannotMarkNewOwnersPublicationComplete() {
        var first = queue.claimPendingPublications(1, NOW, LEASE).getFirst();
        var second = queue.claimPendingPublications(1, NOW.plus(LEASE), LEASE).getFirst();

        queue.markPublished(eventId, first.claimToken(), NOW.plusSeconds(31));

        assertCurrentOwnerUnchanged(second);
        queue.markPublished(eventId, second.claimToken(), NOW.plusSeconds(32));
        assertThat(queue.unpublishedBacklogCount()).isZero();
    }

    @Test
    void expiredOwnerCannotReleaseNewOwnersClaimOrIncrementAttempts() {
        var first = queue.claimPendingPublications(1, NOW, LEASE).getFirst();
        var second = queue.claimPendingPublications(1, NOW.plus(LEASE), LEASE).getFirst();

        queue.releaseFailed(eventId, first.claimToken(), "stale worker", NOW.plusSeconds(31), Duration.ofSeconds(5));

        assertCurrentOwnerUnchanged(second);
        assertThat(jdbc.queryForObject("SELECT attempts FROM message_publication_outbox WHERE event_id=?", Integer.class, eventId))
            .isZero();
    }

    @Test
    void simultaneousRelaysHaveOnlyOneOwnerWithinLease() throws Exception {
        var start = new CyclicBarrier(8);
        try (var workers = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 8).mapToObj(index -> workers.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return queue.claimPendingPublications(1, NOW, LEASE);
            })).toList();
            var claims = new java.util.ArrayList<ClaimedMessagePublication>();
            for (var future : futures) {
                claims.addAll(future.get(10, TimeUnit.SECONDS));
            }
            assertThat(claims).singleElement().satisfies(claim -> assertThat(claim.event().eventId()).isEqualTo(eventId));
        }
    }

    private void assertCurrentOwnerUnchanged(ClaimedMessagePublication owner) {
        var state = jdbc.queryForMap("SELECT claim_token,published_at FROM message_publication_outbox WHERE event_id=?", eventId);
        assertThat(state.get("claim_token")).isEqualTo(owner.claimToken());
        assertThat(state.get("published_at")).isNull();
        assertThat(queue.claimPendingPublications(1, NOW.plusSeconds(31), LEASE)).isEmpty();
    }
}
