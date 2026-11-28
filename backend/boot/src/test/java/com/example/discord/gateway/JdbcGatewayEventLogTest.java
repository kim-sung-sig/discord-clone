package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DelegatingDataSource;
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
             var cleanup = connection.createStatement();
             var user = connection.prepareStatement(
                 "INSERT INTO users(id, username, display_name) VALUES (?, ?, ?)" );
             var guild = connection.prepareStatement(
                 "INSERT INTO guilds(id, name, owner_id) VALUES (?, ?, ?)")) {
            cleanup.executeUpdate("DELETE FROM gateway_event_log");
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
    void concurrentDuplicateInsertReturnsWinnerAfterRollback() throws Exception {
        var event = new GatewayBusEvent(UUID.randomUUID().toString(), "MESSAGE_CREATE", guildId, null,
            Map.of("content", "same"), Instant.now());
        var results = concurrentInsert(event, event);
        assertThat(results[0]).isInstanceOf(GatewayEvent.class);
        assertThat(results[1]).isInstanceOf(GatewayEvent.class);
        assertThat(((GatewayEvent) results[0]).sequence()).isEqualTo(((GatewayEvent) results[1]).sequence());
    }

    @Test
    void concurrentDuplicateInsertRejectsDifferentPayloadAfterRollback() throws Exception {
        var event = new GatewayBusEvent(UUID.randomUUID().toString(), "MESSAGE_CREATE", guildId, null,
            Map.of("content", "first"), Instant.now());
        var different = new GatewayBusEvent(event.eventId(), event.type(), guildId, null,
            Map.of("content", "second"), event.createdAt());
        var results = concurrentInsert(event, different);
        assertThat(results).filteredOn(GatewayEvent.class::isInstance).hasSize(1);
        assertThat(results).filteredOn(GatewayEventConflictException.class::isInstance).hasSize(1);
    }

    private Object[] concurrentInsert(GatewayBusEvent first, GatewayBusEvent second) throws Exception {
        var barrier = new CyclicBarrier(2);
        var uniqueViolations = new AtomicInteger();
        var rollbacks = new AtomicInteger();
        var initialRead = ThreadLocal.withInitial(() -> true);
        DataSource synchronizedSource = new DelegatingDataSource(dataSource) {
            @Override
            public Connection getConnection() throws SQLException {
                Connection connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                    new Class<?>[] {Connection.class}, (proxy, method, args) -> {
                        try {
                            Object result = method.invoke(connection, args);
                            if (method.getName().equals("rollback")) rollbacks.incrementAndGet();
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                && (sql.contains("WHERE event_id = ?") || sql.contains("INSERT INTO gateway_event_log"))) {
                                PreparedStatement statement = (PreparedStatement) result;
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                    new Class<?>[] {PreparedStatement.class}, (p, m, a) -> {
                                        try {
                                            Object value = m.invoke(statement, a);
                                            if (m.getName().equals("executeQuery") && initialRead.get()
                                                && sql.contains("SELECT event_sequence")) {
                                                initialRead.set(false);
                                                barrier.await(10, TimeUnit.SECONDS);
                                            }
                                            return value;
                                        } catch (InvocationTargetException exception) {
                                            if (exception.getCause() instanceof SQLException cause
                                                && "23505".equals(cause.getSQLState())) uniqueViolations.incrementAndGet();
                                            throw exception.getCause();
                                        }
                                    });
                            }
                            return result;
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
            }
        };
        var log = new JdbcGatewayEventLog(synchronizedSource, new ObjectMapper());
        try (var executor = Executors.newFixedThreadPool(2)) {
            var left = executor.submit(() -> appendOrConflict(log, first));
            var right = executor.submit(() -> appendOrConflict(log, second));
            Object[] results = {left.get(20, TimeUnit.SECONDS), right.get(20, TimeUnit.SECONDS)};
            assertThat(uniqueViolations.get()).isEqualTo(1);
            assertThat(rollbacks.get()).isEqualTo(1);
            return results;
        }
    }

    private Object appendOrConflict(JdbcGatewayEventLog log, GatewayBusEvent event) {
        try {
            return log.append(event);
        } catch (GatewayEventConflictException conflict) {
            return conflict;
        }
    }

    @Test
    void latestSequenceIncludesAllocatedValuesBeforeTheirRowsAreAppended() {
        long allocated = eventLog.allocateSequence();

        assertThat(eventLog.latestSequence()).isEqualTo(allocated);
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
