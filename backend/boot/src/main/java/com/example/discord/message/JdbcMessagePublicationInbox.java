package com.example.discord.message;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
final class JdbcMessagePublicationInbox implements MessagePublicationInbox {
    private static final String CONSUMER = "gateway-message-publication";
    private final JdbcTemplate jdbc;

    JdbcMessagePublicationInbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean claim(UUID eventId) {
        return jdbc.update(
            "INSERT INTO consumer_inbox(consumer_name,event_id,processed_at) VALUES (?,?,?) ON CONFLICT DO NOTHING",
            CONSUMER,
            eventId,
            Timestamp.from(Instant.now())
        ) == 1;
    }

    @Override
    public void release(UUID eventId) {
        jdbc.update("DELETE FROM consumer_inbox WHERE consumer_name=? AND event_id=?", CONSUMER, eventId);
    }
}
