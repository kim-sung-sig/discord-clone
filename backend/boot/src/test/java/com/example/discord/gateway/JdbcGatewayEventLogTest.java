package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class JdbcGatewayEventLogTest {
    @Autowired
    private GatewayEventLog eventLog;

    @Autowired
    private DataSource dataSource;

    private UUID guildId;

    @BeforeEach
    void createGuildFixture() throws Exception {
        UUID ownerId = UUID.randomUUID();
        guildId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var user = connection.prepareStatement(
                 "INSERT INTO users(id, username, display_name) VALUES (?, ?, ?)" );
             var guild = connection.prepareStatement(
                 "INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)")) {
            user.setObject(1, ownerId);
            user.setString(2, "gateway_event_" + ownerId.toString().replace("-", "").substring(0, 12));
            user.setString(3, "Gateway Event");
            user.executeUpdate();
            guild.setObject(1, guildId);
            guild.setString(2, "Gateway Event Guild");
            guild.setObject(3, ownerId);
            guild.executeUpdate();
        }
    }

    @Test
    void appendWithSameEventIdReturnsTheOriginalSequence() {
        GatewayBusEvent event = new GatewayBusEvent(
            UUID.randomUUID().toString(), "MESSAGE_CREATE", guildId, null,
            Map.of("content", "durable"), Instant.parse("2026-08-10T00:00:00Z")
        );

        GatewayEvent first = eventLog.append(event);
        GatewayEvent duplicate = eventLog.append(event);

        assertThat(duplicate.sequence()).isEqualTo(first.sequence());
        assertThat(eventLog.after(first.sequence() - 1L, 10)).extracting(GatewayEvent::busEventId)
            .contains(event.eventId());
    }
}
