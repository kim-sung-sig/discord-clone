package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.postgresql.ds.PGSimpleDataSource;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PostgresGatewayEventLogContractTest {
    private DataSource dataSource;
    private JdbcGatewayEventLog eventLog;

    @BeforeAll
    void migrateOnlyTaskDatabase() throws Exception {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setUrl(System.getenv("POSTGRES_JDBC_URL"));
        postgres.setUser(System.getenv().getOrDefault("POSTGRES_USER", "dev_user"));
        postgres.setPassword(System.getenv().getOrDefault("POSTGRES_PASSWORD", "dev_password"));
        try (var connection = postgres.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT current_database()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).startsWith("discord_runtime_guild_");
        }
        dataSource = postgres;
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        eventLog = new JdbcGatewayEventLog(dataSource, new ObjectMapper());
    }

    @Test
    void containsReportsOnlyAppendedEventIds() throws Exception {
        UUID guildId = insertGuild();
        GatewayBusEvent event = new GatewayBusEvent(
            UUID.randomUUID().toString(), "MESSAGE_CREATE", guildId, null, Map.of("content", "durable"), Instant.now());

        assertThat(eventLog.contains(event.eventId())).isFalse();
        eventLog.append(event);

        assertThat(eventLog.contains(event.eventId())).isTrue();
        assertThat(eventLog.contains(UUID.randomUUID().toString())).isFalse();
    }

    @Test
    void containsDoesNotDecodeStoredPayload() throws Exception {
        UUID guildId = insertGuild();
        UUID eventId = UUID.randomUUID();
        // A JSON array cannot be read as the event payload map, so only an existence check can succeed here.
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 INSERT INTO gateway_event_log(
                     event_id, event_type, guild_id, payload_ref, payload_hash, payload_json, created_at, expires_at
                 )
                 VALUES (?, 'MESSAGE_CREATE', ?, ?, ?, '[]'::jsonb, now(), now() + interval '1 day')
                 """)) {
            statement.setObject(1, eventId);
            statement.setObject(2, guildId);
            statement.setString(3, eventId.toString());
            statement.setString(4, "0".repeat(64));
            statement.executeUpdate();
        }

        assertThat(eventLog.contains(eventId.toString())).isTrue();
    }

    private UUID insertGuild() throws Exception {
        UUID ownerId = UUID.randomUUID();
        UUID guildId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var user = connection.prepareStatement("INSERT INTO users(id, username, display_name) VALUES (?, ?, ?)");
             var guild = connection.prepareStatement("INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)")) {
            user.setObject(1, ownerId);
            user.setString(2, "gateway_log_" + ownerId.toString().replace("-", "").substring(0, 12));
            user.setString(3, "Gateway Log");
            user.executeUpdate();
            guild.setObject(1, guildId);
            guild.setString(2, "Gateway Log Guild");
            guild.setObject(3, ownerId);
            guild.executeUpdate();
        }
        return guildId;
    }
}
