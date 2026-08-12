package com.example.discord.gateway;

import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.permission.AuthorizationProjectionStore;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.Permission;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class InMemoryGatewayService implements GatewayCommandService {
    static final int MAX_RETAINED_EVENTS = 1_000;

    private final InMemoryGuildService guildService;
    private final Clock clock;
    private final Duration heartbeatTimeout;
    private final GatewayEventBus eventBus;
    private final GatewaySessionRegistry sessionRegistry;
    private final GatewayEventLog eventLog;
    private final GatewaySessionCursorStore cursorStore;
    private final AuthorizationProjectionStore authorizationProjections;
    private final boolean authorizationProjectionEnabled;
    private final java.util.Set<String> notifiedEventIds = new java.util.HashSet<>();
    private final List<Consumer<GatewayEvent>> listeners = new ArrayList<>();

    public InMemoryGatewayService(InMemoryGuildService guildService, Clock clock, Duration heartbeatTimeout) {
        this(guildService, clock, heartbeatTimeout, new InMemoryGatewayEventBus(clock));
    }

    public InMemoryGatewayService(
        InMemoryGuildService guildService,
        Clock clock,
        Duration heartbeatTimeout,
        GatewayEventBus eventBus
    ) {
        this(guildService, clock, heartbeatTimeout, eventBus, new InMemoryGatewaySessionRegistry());
    }

    public InMemoryGatewayService(
        InMemoryGuildService guildService,
        Clock clock,
        Duration heartbeatTimeout,
        GatewayEventBus eventBus,
        GatewaySessionRegistry sessionRegistry
    ) {
        this(guildService, clock, heartbeatTimeout, eventBus, sessionRegistry, null, false);
    }

    public InMemoryGatewayService(
        InMemoryGuildService guildService,
        Clock clock,
        Duration heartbeatTimeout,
        GatewayEventBus eventBus,
        GatewaySessionRegistry sessionRegistry,
        AuthorizationProjectionStore authorizationProjections
    ) {
        this(guildService, clock, heartbeatTimeout, eventBus, sessionRegistry, authorizationProjections, false);
    }

    public InMemoryGatewayService(
        InMemoryGuildService guildService,
        Clock clock,
        Duration heartbeatTimeout,
        GatewayEventBus eventBus,
        GatewaySessionRegistry sessionRegistry,
        AuthorizationProjectionStore authorizationProjections,
        boolean authorizationProjectionEnabled
    ) {
        this(
            guildService,
            clock,
            heartbeatTimeout,
            eventBus,
            sessionRegistry,
            authorizationProjections,
            authorizationProjectionEnabled,
            new InMemoryGatewayEventLog(),
            new InMemoryGatewaySessionCursorStore()
        );
    }

    public InMemoryGatewayService(
        InMemoryGuildService guildService,
        Clock clock,
        Duration heartbeatTimeout,
        GatewayEventBus eventBus,
        GatewaySessionRegistry sessionRegistry,
        AuthorizationProjectionStore authorizationProjections,
        boolean authorizationProjectionEnabled,
        GatewayEventLog eventLog,
        GatewaySessionCursorStore cursorStore
    ) {
        this.guildService = Objects.requireNonNull(guildService, "guildService must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.heartbeatTimeout = Objects.requireNonNull(heartbeatTimeout, "heartbeatTimeout must not be null");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus must not be null");
        this.sessionRegistry = Objects.requireNonNull(sessionRegistry, "sessionRegistry must not be null");
        this.eventLog = Objects.requireNonNull(eventLog, "eventLog must not be null");
        this.cursorStore = Objects.requireNonNull(cursorStore, "cursorStore must not be null");
        this.authorizationProjections = authorizationProjections;
        this.authorizationProjectionEnabled = authorizationProjectionEnabled;
        this.eventBus.addEventListener(this::appendBusEvent);
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
        sessionRegistry.save(session);
        cursorStore.create(new GatewaySessionCursor(
            session.id(), userId, 0L, ready.sequence(), ready.sequence(), 1L, "in-memory",
            clock.instant().plus(heartbeatTimeout), clock.instant()
        ));
        registerSubscriptions(session);
        ready = ready.withPayload(ready.payloadPlus("sessionId", session.id().toString()));
        return new GatewayIdentifyResult(session, ready);
    }

    public synchronized GatewayHeartbeatResult heartbeat(UUID sessionId, UUID userId) {
        GatewaySession session = requireOwnedSession(sessionId, userId);
        GatewaySession updated = session.withAck(clock.instant());
        sessionRegistry.save(updated);
        GatewayEvent ack = controlEvent("HEARTBEAT_ACK", Map.of("sessionId", updated.id().toString()));
        return new GatewayHeartbeatResult(updated, ack);
    }

    public synchronized List<GatewaySession> closeTimedOutSessions() {
        List<GatewaySession> closed = new ArrayList<>();
        for (GatewaySession session : sessionRegistry.sessions()) {
            if (!session.closed() && session.lastAcknowledgedAt().plus(heartbeatTimeout).isBefore(clock.instant())) {
                GatewaySession updated = session.close();
                sessionRegistry.save(updated);
                closed.add(updated);
            }
        }
        return List.copyOf(closed);
    }

    public synchronized GatewayEvent publish(String type, UUID guildId, UUID channelId, Map<String, Object> payload) {
        return publish(null, type, guildId, channelId, payload);
    }

    public synchronized GatewayEvent publish(
        UUID sourceEventId,
        String type,
        UUID guildId,
        UUID channelId,
        Map<String, Object> payload
    ) {
        Objects.requireNonNull(guildId, "guildId must not be null");
        if (channelId != null && !guildService.channelBelongsToGuild(guildId, channelId)) {
            throw new IllegalArgumentException("channel does not belong to guild");
        }
        GatewayBusEvent busEvent = eventBus.publish(new GatewayBusPublishCommand(
            type, guildId, channelId, payload, sourceEventId == null ? null : sourceEventId.toString()
        ));
        return appendBusEvent(busEvent);
    }

    public synchronized void addEventListener(Consumer<GatewayEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    public synchronized List<GatewayEvent> poll(UUID sessionId, UUID userId, long afterSequence) {
        GatewaySession session = requireOwnedSession(sessionId, userId);
        registerSubscriptions(session);
        List<GatewayEvent> deliverable = deliverableEvents(session, afterSequence, true);
        updateLastDelivered(session, deliverable);
        return deliverable;
    }

    public synchronized GatewaySessionCursor acknowledge(UUID sessionId, UUID userId, long sequence) {
        GatewaySessionCursor current = sessionCursor(sessionId, userId);
        return acknowledge(sessionId, userId, current.deliveryEpoch(), current.ownerInstanceId(), sequence);
    }

    public synchronized GatewaySessionCursor acknowledge(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence
    ) {
        requireOwnedSession(sessionId, userId);
        return cursorStore.acknowledge(sessionId, userId, deliveryEpoch, ownerInstanceId, sequence);
    }

    public synchronized GatewaySessionCursor sessionCursor(UUID sessionId, UUID userId) {
        requireOwnedSession(sessionId, userId);
        return cursorStore.find(sessionId, userId)
            .orElseThrow(() -> new GatewaySessionNotFoundException("gateway session cursor not found"));
    }

    public synchronized GatewayResumeResult resume(UUID sessionId, UUID userId, long lastSequence) {
        GatewaySession session = requireOwnedSession(sessionId, userId);
        GatewaySessionCursor current = cursorStore.find(sessionId, userId).orElse(null);
        if (current == null) {
            cursorStore.create(new GatewaySessionCursor(
                sessionId, userId, session.lastDeliveredSequence(), session.lastDeliveredSequence(),
                session.lastDeliveredSequence(), 1L, "in-memory",
                clock.instant().plus(heartbeatTimeout), clock.instant()
            ));
            current = cursorStore.find(sessionId, userId)
                .orElseThrow(() -> new GatewaySessionNotFoundException("gateway session cursor not found"));
        }
        long replayFrom = current.acknowledgedUserSequence();
        if (replayFrom > 0L && replayFrom < Math.max(0L, eventLog.oldestSequence() - 1L)) {
            throw new GatewayResyncRequiredException("gateway event retention no longer covers requested sequence");
        }
        registerSubscriptions(session);
        cursorStore.replaceEpoch(
            sessionId,
            userId,
            "in-memory",
            clock.instant().plus(heartbeatTimeout),
            clock.instant()
        );
        GatewayEvent resumed = controlEvent("RESUMED", Map.of("sessionId", session.id().toString()));
        List<GatewayEvent> deliverable = deliverableEvents(session, replayFrom, false);
        GatewaySession updated = updateLastDelivered(session, deliverable);
        return new GatewayResumeResult(updated, resumed, deliverable);
    }

    public synchronized GatewaySession session(UUID sessionId) {
        return requireSession(sessionId);
    }

    private GatewayEvent controlEvent(String type, Map<String, Object> payload) {
        return new GatewayEvent(eventLog.allocateSequence(), type, null, null, payload, clock.instant());
    }

    private List<GatewayEvent> deliverableEvents(
        GatewaySession session,
        long afterSequence,
        boolean suppressAlreadyDelivered
    ) {
        long lowerBound = suppressAlreadyDelivered
            ? Math.max(afterSequence, session.lastDeliveredSequence())
            : afterSequence;
        return eventLog.after(lowerBound, MAX_RETAINED_EVENTS).stream()
            .filter(event -> canDeliver(session.userId(), event))
            .toList();
    }

    private GatewaySession updateLastDelivered(GatewaySession session, List<GatewayEvent> delivered) {
        long lastSequence = delivered.stream()
            .mapToLong(GatewayEvent::sequence)
            .max()
            .orElse(session.lastDeliveredSequence());
        GatewaySession updated = session.withLastDeliveredSequence(lastSequence);
        sessionRegistry.save(updated);
        if (!delivered.isEmpty()) {
            GatewaySessionCursor cursor = cursorStore.find(session.id(), session.userId())
                .orElseThrow(() -> new GatewaySessionNotFoundException("gateway session cursor not found"));
            cursorStore.markDelivered(
                session.id(), session.userId(), cursor.deliveryEpoch(), cursor.ownerInstanceId(),
                lastSequence, clock.instant()
            );
        }
        return updated;
    }

    private boolean canDeliver(UUID userId, GatewayEvent event) {
        if (event.guildId() == null) {
            return true;
        }
        if (event.channelId() != null) {
            if (authorizationProjectionEnabled && authorizationProjections != null) {
                return authorizationProjections.decide(event.guildId(), userId, AuthorizationResourceType.CHANNEL,
                    event.channelId(), Permission.VIEW_CHANNEL).allowed();
            }
            return guildService.canViewChannel(event.guildId(), event.channelId(), userId);
        }
        return guildService.isGuildMemberOrOwner(event.guildId(), userId);
    }

    private void registerSubscriptions(GatewaySession session) {
        for (UUID guildId : session.guildIds()) {
            eventBus.subscribeGuild(guildId);
            guildService.visibleChannels(guildId, session.userId())
                .forEach(channel -> eventBus.subscribeChannel(channel.id()));
        }
    }

    private synchronized GatewayEvent appendBusEvent(GatewayBusEvent busEvent) {
        if (busEvent.channelId() != null && !guildService.channelBelongsToGuild(busEvent.guildId(), busEvent.channelId())) {
            throw new IllegalArgumentException("channel does not belong to guild");
        }
        long highestSessionSequence = sessionRegistry.sessions().stream()
            .mapToLong(GatewaySession::lastDeliveredSequence)
            .max()
            .orElse(0L);
        while (eventLog.latestSequence() < highestSessionSequence) {
            eventLog.allocateSequence();
        }
        GatewayEvent event = eventLog.append(busEvent);
        if (notifiedEventIds.add(busEvent.eventId())) {
            for (Consumer<GatewayEvent> listener : List.copyOf(listeners)) {
                listener.accept(event);
            }
        }
        return event;
    }

    private GatewaySession requireOwnedSession(UUID sessionId, UUID userId) {
        GatewaySession session = requireActiveSession(sessionId);
        if (!session.userId().equals(userId)) {
            throw new GatewayForbiddenException("session owner required");
        }
        return session;
    }

    private GatewaySession requireActiveSession(UUID sessionId) {
        GatewaySession session = sessionRegistry.find(sessionId).orElse(null);
        if (session == null) {
            throw new GatewaySessionNotFoundException("gateway session not found");
        }
        if (session.closed()) {
            throw new GatewayForbiddenException("gateway session closed");
        }
        return session;
    }

    private GatewaySession requireSession(UUID sessionId) {
        GatewaySession session = sessionRegistry.find(sessionId).orElse(null);
        if (session == null) {
            throw new GatewaySessionNotFoundException("gateway session not found");
        }
        return session;
    }
}
