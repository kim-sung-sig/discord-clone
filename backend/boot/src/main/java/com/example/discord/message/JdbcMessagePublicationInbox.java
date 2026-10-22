package com.example.discord.message;

import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class JdbcMessagePublicationInbox implements MessagePublicationInbox {
    private static final String CONSUMER = "gateway-message-publication";
    private final JdbcTemplate jdbc;

    JdbcMessagePublicationInbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isProcessed(UUID eventId) {
        Boolean processed = jdbc.queryForObject(
            "SELECT EXISTS(SELECT 1 FROM consumer_inbox WHERE consumer_name=? AND event_id=?)",
            Boolean.class,
            CONSUMER,
            eventId
        );
        return Boolean.TRUE.equals(processed);
    }

    @Override
    public void markProcessed(UUID eventId) {
        jdbc.update(
            "INSERT INTO consumer_inbox(consumer_name,event_id,processed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            CONSUMER,
            eventId,
            java.sql.Timestamp.from(java.time.Instant.now())
        );
    }
}
