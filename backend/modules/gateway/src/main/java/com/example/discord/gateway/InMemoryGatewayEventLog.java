package com.example.discord.gateway;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class InMemoryGatewayEventLog implements GatewayEventLog {
    private static final int MAX_RETAINED_EVENTS = 1_000;

    private final Map<String, StoredEvent> byEventId = new LinkedHashMap<>();
    private final List<GatewayEvent> events = new ArrayList<>();
    private long nextSequence = 1L;

    @Override
    public synchronized long allocateSequence() {
        return nextSequence++;
    }

    @Override
    public synchronized GatewayEvent append(GatewayBusEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        String hash = GatewayEventPayloadHash.of(event);
        StoredEvent existing = byEventId.get(event.eventId());
        if (existing != null) {
            if (!existing.payloadHash().equals(hash)) {
                throw new GatewayEventConflictException("gateway event payload hash conflict");
            }
            return existing.event();
        }
        GatewayEvent appended = new GatewayEvent(
            allocateSequence(),
            event.eventId(),
            event.type(),
            event.guildId(),
            event.channelId(),
            event.payload(),
            event.createdAt()
        );
        byEventId.put(event.eventId(), new StoredEvent(hash, appended));
        events.add(appended);
        while (events.size() > MAX_RETAINED_EVENTS) {
            GatewayEvent evicted = events.removeFirst();
            byEventId.remove(evicted.busEventId());
        }
        return appended;
    }

    @Override
    public synchronized List<GatewayEvent> after(long sequence, int limit) {
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must not be negative");
        }
        int boundedLimit = Math.max(1, Math.min(limit, MAX_RETAINED_EVENTS));
        return events.stream()
            .filter(event -> event.sequence() > sequence)
            .limit(boundedLimit)
            .toList();
    }

    @Override
    public synchronized long oldestSequence() {
        return events.isEmpty() ? nextSequence : events.getFirst().sequence();
    }

    @Override
    public synchronized long latestSequence() {
        return nextSequence - 1L;
    }

    private record StoredEvent(String payloadHash, GatewayEvent event) {
    }
}
