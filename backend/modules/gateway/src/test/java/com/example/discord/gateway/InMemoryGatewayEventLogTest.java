package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryGatewayEventLogTest {
    private final InMemoryGatewayEventLog eventLog = new InMemoryGatewayEventLog();

    @Test
    void sameEventIdWithDifferentPayloadIsRejected() {
        String eventId = UUID.randomUUID().toString();
        GatewayBusEvent first = event(eventId, "one");
        GatewayBusEvent conflicting = event(eventId, "two");

        eventLog.append(first);

        assertThatThrownBy(() -> eventLog.append(conflicting))
            .isInstanceOf(GatewayEventConflictException.class);
    }

    @Test
    void duplicateEventReturnsOriginalSequence() {
        GatewayBusEvent event = event(UUID.randomUUID().toString(), "same");

        GatewayEvent first = eventLog.append(event);
        GatewayEvent duplicate = eventLog.append(event);

        assertThat(duplicate.sequence()).isEqualTo(first.sequence());
        assertThat(eventLog.latestSequence()).isEqualTo(first.sequence());
    }

    private static GatewayBusEvent event(String eventId, String content) {
        return new GatewayBusEvent(
            eventId, "MESSAGE_CREATE", UUID.randomUUID(), null, Map.of("content", content),
            Instant.parse("2026-08-10T00:00:00Z")
        );
    }
}
