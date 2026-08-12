package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.LinkedHashMap;
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

    @Test
    void retryWithSameEventIdAndPayloadIgnoresNewPublishTimestamp() {
        String eventId = UUID.randomUUID().toString();
        UUID guildId = UUID.randomUUID();
        GatewayBusEvent first = new GatewayBusEvent(
            eventId, "MESSAGE_CREATE", guildId, null, Map.of("content", "same"),
            Instant.parse("2026-08-12T00:00:00Z")
        );
        GatewayBusEvent retry = new GatewayBusEvent(
            eventId, "MESSAGE_CREATE", guildId, null, Map.of("content", "same"),
            Instant.parse("2026-08-12T00:00:01Z")
        );

        GatewayEvent appended = eventLog.append(first);
        GatewayEvent duplicate = eventLog.append(retry);

        assertThat(duplicate.sequence()).isEqualTo(appended.sequence());
        assertThat(duplicate.createdAt()).isEqualTo(first.createdAt());
    }

    @Test
    void retryWithSameEventIdAndReorderedPayloadKeysIsIdempotent() {
        String eventId = UUID.randomUUID().toString();
        Map<String, Object> firstPayload = new LinkedHashMap<>();
        firstPayload.put("zeta", Map.of("b", 2, "a", 1));
        firstPayload.put("alpha", "same");
        Map<String, Object> retryPayload = new LinkedHashMap<>();
        retryPayload.put("alpha", "same");
        retryPayload.put("zeta", Map.of("a", 1, "b", 2));

        GatewayEvent first = eventLog.append(new GatewayBusEvent(
            eventId, "MESSAGE_CREATE", UUID.randomUUID(), null, firstPayload, Instant.now()));
        GatewayEvent retry = eventLog.append(new GatewayBusEvent(
            eventId, "MESSAGE_CREATE", first.guildId(), null, retryPayload, Instant.now()));

        assertThat(retry.sequence()).isEqualTo(first.sequence());
    }

    private static GatewayBusEvent event(String eventId, String content) {
        return new GatewayBusEvent(
            eventId, "MESSAGE_CREATE", UUID.randomUUID(), null, Map.of("content", content),
            Instant.parse("2026-08-10T00:00:00Z")
        );
    }
}
