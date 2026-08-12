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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
@Testcontainers
class JdbcGatewayEventLogTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("discord")
        .withUsername("gateway_test")
        .withPassword("gateway_test_password");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
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
            Map.of("content", "durable"), Instant.now()
        );

        GatewayEvent first = eventLog.append(event);
        GatewayEvent duplicate = eventLog.append(event);

        assertThat(duplicate.sequence()).isEqualTo(first.sequence());
        assertThat(eventLog.after(first.sequence() - 1L, 10)).extracting(GatewayEvent::busEventId)
            .contains(event.eventId());
    }

    @Test
    void oldestSequenceIgnoresExpiredRows() throws Exception {
        GatewayBusEvent expired = new GatewayBusEvent(
            UUID.randomUUID().toString(), "MESSAGE_CREATE", guildId, null,
            Map.of("content", "expired"), Instant.now()
        );
        GatewayBusEvent live = new GatewayBusEvent(
            UUID.randomUUID().toString(), "MESSAGE_CREATE", guildId, null,
            Map.of("content", "live"), Instant.now()
        );

        GatewayEvent expiredEvent = eventLog.append(expired);
        GatewayEvent liveEvent = eventLog.append(live);
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "UPDATE gateway_event_log SET expires_at = now() - interval '1 minute' WHERE event_sequence = ?")) {
            statement.setLong(1, expiredEvent.sequence());
            statement.executeUpdate();
        }

        assertThat(eventLog.oldestSequence()).isEqualTo(liveEvent.sequence());
    }
}
