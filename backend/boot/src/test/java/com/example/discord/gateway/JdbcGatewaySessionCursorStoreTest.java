package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
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
class JdbcGatewaySessionCursorStoreTest {
    @Autowired
    private GatewaySessionCursorStore cursorStore;

    @Autowired
    private DataSource dataSource;

    private UUID userId;

    @BeforeEach
    void createUserFixture() throws Exception {
        userId = UUID.randomUUID();
        try (var connection = dataSource.getConnection();
             var user = connection.prepareStatement(
                 "INSERT INTO users(id, username, display_name) VALUES (?, ?, ?)")) {
            user.setObject(1, userId);
            user.setString(2, "gateway_cursor_" + userId.toString().replace("-", "").substring(0, 12));
            user.setString(3, "Gateway Cursor");
            user.executeUpdate();
        }
    }

    @Test
    void deliveredAndAcknowledgedSequencesRemainMonotonic() {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-10T00:00:00Z");
        cursorStore.create(new GatewaySessionCursor(
            sessionId, userId, 0L, 0L, 0L, 1L, "test-instance", now.plusSeconds(30), now
        ));

        cursorStore.markDelivered(sessionId, userId, 3L, now);
        GatewaySessionCursor acknowledged = cursorStore.acknowledge(sessionId, userId, 2L);

        assertThat(acknowledged.acknowledgedUserSequence()).isEqualTo(2L);
        assertThat(acknowledged.highestDeliveredUserSequence()).isEqualTo(3L);
    }
}
