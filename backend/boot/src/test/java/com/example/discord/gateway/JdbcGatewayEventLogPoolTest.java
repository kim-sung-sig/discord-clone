package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class JdbcGatewayEventLogPoolTest {
    @Test
    void duplicateInsertRecoversWithOneConnection() throws Exception {
        var event = new GatewayBusEvent(UUID.randomUUID().toString(), "MESSAGE_CREATE", UUID.randomUUID(), null,
            Map.of("content", "durable"), Instant.now());
        var physical = mock(Connection.class);
        var source = mock(DataSource.class);
        when(source.getConnection()).thenReturn(physical);
        when(physical.isValid(5)).thenReturn(true);
        when(physical.getAutoCommit()).thenReturn(true);
        var select = mock(PreparedStatement.class);
        var insert = mock(PreparedStatement.class);
        var hash = mock(PreparedStatement.class);
        when(physical.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            return sql.contains("INSERT INTO") ? insert : sql.contains("payload_hash FROM") ? hash : select;
        });
        var absent = mock(ResultSet.class);
        var present = mock(ResultSet.class);
        when(select.executeQuery()).thenReturn(absent, present, present);
        when(present.next()).thenReturn(true);
        when(present.getLong("event_sequence")).thenReturn(42L);
        when(present.getObject("event_id", UUID.class)).thenReturn(UUID.fromString(event.eventId()));
        when(present.getObject("guild_id", UUID.class)).thenReturn(event.guildId());
        when(present.getString("event_type")).thenReturn(event.type());
        when(present.getString("payload_json")).thenReturn("{\"content\":\"durable\"}");
        when(present.getTimestamp("created_at")).thenReturn(Timestamp.from(event.createdAt()));
        when(insert.executeQuery()).thenThrow(new SQLException("duplicate", "23505"));
        var hashRow = mock(ResultSet.class);
        when(hash.executeQuery()).thenReturn(hashRow);
        when(hashRow.next()).thenReturn(true);
        when(hashRow.getString(1)).thenReturn(GatewayEventPayloadHash.of(event));
        var config = new HikariConfig();
        config.setDataSource(source);
        config.setMaximumPoolSize(1);
        config.setConnectionTimeout(250);
        try (var pool = new HikariDataSource(config)) {
            var eventLog = new JdbcGatewayEventLog(pool, new ObjectMapper());
            try (var executor = Executors.newFixedThreadPool(2)) {
                var first = executor.submit(() -> eventLog.append(event));
                var second = executor.submit(() -> eventLog.append(event));
                assertThat(first.get(3, TimeUnit.SECONDS).sequence()).isEqualTo(42L);
                assertThat(second.get(3, TimeUnit.SECONDS).sequence()).isEqualTo(42L);
            }
        }
    }
}
