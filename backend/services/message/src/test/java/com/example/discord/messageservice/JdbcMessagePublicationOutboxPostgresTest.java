package com.example.discord.messageservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.message.ClaimedMessagePublication;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Properties;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class JdbcMessagePublicationOutboxPostgresTest {
    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");
    private static final String TEST_SCHEMA = "t171c_message_outbox_test";

    private JdbcTemplate jdbc;
    private JdbcMessagePublicationOutbox outbox;

    @BeforeEach
    void clean() {
        var source = new DriverManagerDataSource(
            envOrDefault("POSTGRES_JDBC_URL", "jdbc:postgresql://127.0.0.1:15432/discord_message"),
            envOrDefault("POSTGRES_USER", "dev_user"), envOrDefault("POSTGRES_PASSWORD", "dev_password")
        );
        var properties = new Properties();
        properties.setProperty("currentSchema", TEST_SCHEMA);
        source.setConnectionProperties(properties);
        Flyway.configure().dataSource(source).schemas(TEST_SCHEMA).defaultSchema(TEST_SCHEMA)
            .locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        outbox = new JdbcMessagePublicationOutbox(jdbc);
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

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
