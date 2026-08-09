package com.example.discord.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
@DependsOn("postgresFlyway")
final class JdbcGatewayEventLog implements GatewayEventLog {
    private static final Logger log = LoggerFactory.getLogger(JdbcGatewayEventLog.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };
    private static final Duration RETENTION = Duration.ofDays(7);

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    JdbcGatewayEventLog(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public long allocateSequence() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT nextval(pg_get_serial_sequence('gateway_event_log', 'event_sequence'))")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to allocate gateway event sequence", exception);
        }
    }

    @Override
    public GatewayEvent append(GatewayBusEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        String hash = GatewayEventPayloadHash.of(event);
        String payload = writePayload(event.payload());
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                GatewayEvent existing = findByEventId(connection, event.eventId());
                if (existing != null) {
                    verifyHash(connection, event.eventId(), hash);
                    connection.commit();
                    return existing;
                }
                try (var statement = connection.prepareStatement("""
                    INSERT INTO gateway_event_log(
                        event_id, event_type, guild_id, channel_id, payload_ref, payload_hash,
                        payload_json, created_at, expires_at
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    RETURNING event_sequence
                    """)) {
                    statement.setObject(1, UUID.fromString(event.eventId()));
                    statement.setString(2, event.type());
                    statement.setObject(3, event.guildId());
                    statement.setObject(4, event.channelId());
                    statement.setString(5, event.eventId());
                    statement.setString(6, hash);
                    statement.setString(7, payload);
                    statement.setTimestamp(8, Timestamp.from(event.createdAt()));
                    statement.setTimestamp(9, Timestamp.from(event.createdAt().plus(RETENTION)));
                    try (ResultSet resultSet = statement.executeQuery()) {
                        resultSet.next();
                        GatewayEvent appended = new GatewayEvent(
                            resultSet.getLong("event_sequence"), event.eventId(), event.type(), event.guildId(),
                            event.channelId(), event.payload(), event.createdAt()
                        );
                        connection.commit();
                        log.info("Gateway event log appended eventId={} sequence={}", event.eventId(), appended.sequence());
                        return appended;
                    }
                }
            } catch (SQLException exception) {
                connection.rollback();
                if ("23505".equals(exception.getSQLState())) {
                    try (Connection retryConnection = dataSource.getConnection()) {
                        GatewayEvent existing = findByEventId(retryConnection, event.eventId());
                        if (existing != null) {
                            verifyHash(retryConnection, event.eventId(), hash);
                            return existing;
                        }
                    }
                }
                throw exception;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to append gateway event", exception);
        }
    }

    @Override
    public List<GatewayEvent> after(long sequence, int limit) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        int boundedLimit = Math.max(1, Math.min(limit, 100));
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("""
                 SELECT event_sequence, event_id, event_type, guild_id, channel_id, payload_json, created_at
                 FROM gateway_event_log
                 WHERE event_sequence > ? AND expires_at > now()
                 ORDER BY event_sequence
                 LIMIT ?
                 """)) {
            statement.setLong(1, sequence);
            statement.setInt(2, boundedLimit);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<GatewayEvent> events = new ArrayList<>();
                while (resultSet.next()) {
                    events.add(from(resultSet));
                }
                return List.copyOf(events);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read gateway event log", exception);
        }
    }

    @Override
    public long oldestSequence() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                 "SELECT COALESCE(MIN(event_sequence), COALESCE(MAX(event_sequence), 0) + 1) FROM gateway_event_log")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read oldest gateway sequence", exception);
        }
    }

    @Override
    public long latestSequence() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT COALESCE(MAX(event_sequence), 0) FROM gateway_event_log")) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read latest gateway sequence", exception);
        }
    }

    private GatewayEvent findByEventId(Connection connection, String eventId) throws SQLException {
        try (var statement = connection.prepareStatement("""
            SELECT event_sequence, event_id, event_type, guild_id, channel_id, payload_json, created_at
            FROM gateway_event_log
            WHERE event_id = ?
            """)) {
            statement.setObject(1, UUID.fromString(eventId));
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? from(resultSet) : null;
            }
        }
    }

    private void verifyHash(Connection connection, String eventId, String expectedHash) throws SQLException {
        try (var statement = connection.prepareStatement(
            "SELECT payload_hash FROM gateway_event_log WHERE event_id = ?")) {
            statement.setObject(1, UUID.fromString(eventId));
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return;
                }
                if (!expectedHash.equals(resultSet.getString(1))) {
                    throw new GatewayEventConflictException("gateway event payload hash conflict");
                }
            }
        }
    }

    private GatewayEvent from(ResultSet resultSet) throws SQLException {
        try {
            return new GatewayEvent(
                resultSet.getLong("event_sequence"),
                resultSet.getObject("event_id", UUID.class).toString(),
                resultSet.getString("event_type"),
                resultSet.getObject("guild_id", UUID.class),
                resultSet.getObject("channel_id", UUID.class),
                objectMapper.readValue(resultSet.getString("payload_json"), PAYLOAD_TYPE),
                resultSet.getTimestamp("created_at").toInstant()
            );
        } catch (JsonProcessingException exception) {
            throw new SQLException("gateway event payload is not valid JSON", exception);
        }
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("gateway payload is not serializable", exception);
        }
    }
}
