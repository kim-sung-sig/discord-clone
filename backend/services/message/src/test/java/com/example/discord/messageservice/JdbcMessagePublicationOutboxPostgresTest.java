package com.example.discord.messageservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.message.ClaimedMessagePublication;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
@ActiveProfiles({"postgres", "kafka"})
@SpringBootTest
class JdbcMessagePublicationOutboxPostgresTest {
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");
    private static final String TEST_SCHEMA = "t171c_message_outbox_test";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("discord.auth.jwt.issuer", () -> "discord-identity");
        registry.add("discord.auth.jwt.audience", () -> "discord-api");
        registry.add("discord.auth.jwt.key-id", () -> "identity-2026-07");
        registry.add("discord.auth.jwt.public-key-locations.identity-2026-07", () -> keyFixture("ed25519-public.pem"));
        registry.add("spring.kafka.bootstrap-servers", () -> envOrDefault(
            "SPRING_KAFKA_BOOTSTRAP_SERVERS",
            "127.0.0.1:29092"
        ));
        registry.add("spring.datasource.url", () -> envOrDefault(
            "POSTGRES_JDBC_URL",
            "jdbc:postgresql://127.0.0.1:15432/discord_message"
        ));
        registry.add("spring.flyway.schemas", () -> TEST_SCHEMA);
        registry.add("spring.flyway.default-schema", () -> TEST_SCHEMA);
        registry.add("spring.flyway.create-schemas", () -> "true");
        registry.add("spring.datasource.hikari.connection-init-sql", () ->
            "SET search_path TO " + TEST_SCHEMA
        );
    }

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JdbcMessagePublicationOutbox outbox;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM message_publication_outbox");
    }

    @Test
    void brokerAckMarkAndFailureRetryToDeadLetterArePersisted() {
        UUID eventId = UUID.randomUUID();
        insert(eventId);

        ClaimedMessagePublication claim = outbox.claimPendingPublications(1, NOW, Duration.ofSeconds(30)).getFirst();
        outbox.markPublished(eventId, claim.claimToken(), NOW.plusSeconds(1));
        assertThat(jdbc.queryForObject(
            "SELECT published_at IS NOT NULL FROM message_publication_outbox WHERE event_id=?",
            Boolean.class,
            eventId
        )).isTrue();

        UUID failedEventId = UUID.randomUUID();
        insert(failedEventId);
        for (int attempt = 1; attempt <= 10; attempt++) {
            ClaimedMessagePublication failed = outbox.claimPendingPublications(
                1,
                NOW.plusSeconds(attempt * 31L),
                Duration.ofSeconds(30)
            ).getFirst();
            outbox.releaseFailed(
                failedEventId,
                failed.claimToken(),
                "broker unavailable",
                NOW.plusSeconds(attempt * 31L),
                Duration.ofSeconds(1)
            );
        }

        assertThat(jdbc.queryForObject(
            "SELECT attempts FROM message_publication_outbox WHERE event_id=?",
            Integer.class,
            failedEventId
        )).isEqualTo(10);
        assertThat(jdbc.queryForObject(
            "SELECT dead_lettered_at IS NOT NULL FROM message_publication_outbox WHERE event_id=?",
            Boolean.class,
            failedEventId
        )).isTrue();
    }

    private void insert(UUID eventId) {
        jdbc.update("""
            INSERT INTO message_publication_outbox(
                event_id,event_type,message_id,author_type,author_id,target_type,
                guild_id,channel_id,correlation_id,occurred_at
            ) VALUES (?,?,?,?,?,?,?,?,?,?)
            """,
            eventId,
            "MessagePublished",
            UUID.randomUUID(),
            "USER",
            UUID.randomUUID(),
            "CHANNEL",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "postgres-outbox-test",
            Timestamp.from(NOW)
        );
    }

    private static String keyFixture(String name) {
        return "file:" + Path.of("..", "..", "modules", "identity", "src", "test", "resources", "identity", name)
            .toAbsolutePath();
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
