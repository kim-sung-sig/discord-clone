package com.example.discord.gateway;

import com.example.discord.guild.InMemoryGuildService;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class InMemoryGatewayService {
    private final InMemoryGuildService guildService;
    private final Clock clock;
    private final Duration heartbeatTimeout;
    private final Map<UUID, GatewaySession> sessions = new LinkedHashMap<>();
    private final List<GatewayEvent> events = new ArrayList<>();
    private long nextSequence = 1L;

    public InMemoryGatewayService(InMemoryGuildService guildService, Clock clock, Duration heartbeatTimeout) {
        this.guildService = Objects.requireNonNull(guildService, "guildService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.heartbeatTimeout = Objects.requireNonNull(heartbeatTimeout, "heartbeatTimeout must not be null");
    }

    public synchronized GatewayIdentifyResult identify(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        Set<UUID> guildIds = Set.copyOf(guildService.guildIdsForMember(userId));
        GatewayEvent ready = controlEvent(
            "READY",
            Map.of("userId", userId.toString(), "guildIds", guildIds.stream().map(UUID::toString).sorted().toList())
        );
        GatewaySession session = new GatewaySession(
            UUID.randomUUID(),
            userId,
            guildIds,
            clock.instant(),
            false,
            ready.sequence()
        );
        sessions.put(session.id(), session);
        ready = ready.withPayload(ready.payloadPlus("sessionId", session.id().toString()));
        return new GatewayIdentifyResult(session, ready);
    }

    public synchronized GatewayHeartbeatResult heartbeat(UUID sessionId, UUID userId) {
        GatewaySession session = requireOwnedSession(sessionId, userId);
        GatewaySession updated = session.withAck(clock.instant());
        sessions.put(updated.id(), updated);
        GatewayEvent ack = controlEvent("HEARTBEAT_ACK", Map.of("sessionId", updated.id().toString()));
        return new GatewayHeartbeatResult(updated, ack);
    }

    public synchronized List<GatewaySession> closeTimedOutSessions() {
        List<GatewaySession> closed = new ArrayList<>();
        for (GatewaySession session : sessions.values()) {
            if (!session.closed() && session.lastAcknowledgedAt().plus(heartbeatTimeout).isBefore(clock.instant())) {
                GatewaySession updated = session.close();
                sessions.put(updated.id(), updated);
                closed.add(updated);
            }
        }
        return List.copyOf(closed);
    }

    public synchronized GatewayEvent publish(String type, UUID guildId, UUID channelId, Map<String, Object> payload) {
        Objects.requireNonNull(guildId, "guildId must not be null");
        if (channelId != null && !guildService.channelBelongsToGuild(guildId, channelId)) {
            throw new IllegalArgumentException("channel does not belong to guild");
        }
        GatewayEvent event = new GatewayEvent(nextSequence++, type, guildId, channelId, payload, clock.instant());
        events.add(event);
        return event;
    }

    public synchronized List<GatewayEvent> poll(UUID sessionId, UUID userId, long afterSequence) {
        GatewaySession session = requireOwnedSession(sessionId, userId);
        List<GatewayEvent> deliverable = deliverableEvents(session, afterSequence);
        updateLastDelivered(session, deliverable);
        return deliverable;
    }

    public synchronized GatewayResumeResult resume(UUID sessionId, UUID userId, long lastSequence) {
        GatewaySession session = requireOwnedSession(sessionId, userId);
        GatewayEvent resumed = controlEvent("RESUMED", Map.of("sessionId", session.id().toString()));
        List<GatewayEvent> deliverable = deliverableEvents(session, lastSequence);
        GatewaySession updated = updateLastDelivered(session, deliverable);
        return new GatewayResumeResult(updated, resumed, deliverable);
    }

    public synchronized GatewaySession session(UUID sessionId) {
        return requireSession(sessionId);
    }

    private GatewayEvent controlEvent(String type, Map<String, Object> payload) {
        return new GatewayEvent(nextSequence++, type, null, null, payload, clock.instant());
    }

    private List<GatewayEvent> deliverableEvents(GatewaySession session, long afterSequence) {
        long lowerBound = Math.max(afterSequence, session.lastDeliveredSequence());
        return events.stream()
            .filter(event -> event.sequence() > lowerBound)
            .filter(event -> canDeliver(session.userId(), event))
            .sorted(Comparator.comparingLong(GatewayEvent::sequence))
            .toList();
    }

    private GatewaySession updateLastDelivered(GatewaySession session, List<GatewayEvent> delivered) {
        long lastSequence = delivered.stream()
            .mapToLong(GatewayEvent::sequence)
            .max()
            .orElse(session.lastDeliveredSequence());
        GatewaySession updated = session.withLastDeliveredSequence(lastSequence);
        sessions.put(updated.id(), updated);
        return updated;
    }

    private boolean canDeliver(UUID userId, GatewayEvent event) {
        if (event.channelId() != null) {
            return guildService.canViewChannel(event.guildId(), event.channelId(), userId);
        }
        return guildService.isGuildMemberOrOwner(event.guildId(), userId);
    }

    private GatewaySession requireOwnedSession(UUID sessionId, UUID userId) {
        GatewaySession session = requireActiveSession(sessionId);
        if (!session.userId().equals(userId)) {
            throw new GatewayForbiddenException("session owner required");
        }
        return session;
    }

    private GatewaySession requireActiveSession(UUID sessionId) {
        GatewaySession session = sessions.get(sessionId);
        if (session == null) {
            throw new GatewaySessionNotFoundException("gateway session not found");
        }
        if (session.closed()) {
            throw new GatewayForbiddenException("gateway session closed");
        }
        return session;
    }

    private GatewaySession requireSession(UUID sessionId) {
        GatewaySession session = sessions.get(sessionId);
        if (session == null) {
            throw new GatewaySessionNotFoundException("gateway session not found");
        }
        return session;
    }
}
