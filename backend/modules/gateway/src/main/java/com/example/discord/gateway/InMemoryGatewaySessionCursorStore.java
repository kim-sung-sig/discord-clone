package com.example.discord.gateway;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryGatewaySessionCursorStore implements GatewaySessionCursorStore {
    private final Map<UUID, GatewaySessionCursor> cursors = new LinkedHashMap<>();

    @Override
    public synchronized void create(GatewaySessionCursor cursor) {
        Objects.requireNonNull(cursor, "cursor must not be null");
        cursors.put(cursor.sessionId(), cursor);
    }

    @Override
    public synchronized Optional<GatewaySessionCursor> find(UUID sessionId, UUID userId) {
        GatewaySessionCursor cursor = cursors.get(sessionId);
        if (cursor == null || !cursor.userId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(cursor);
    }

    @Override
    public synchronized GatewaySessionCursor markDelivered(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence,
        Instant now
    ) {
        GatewaySessionCursor cursor = require(sessionId, userId);
        validateOwner(cursor, deliveryEpoch, ownerInstanceId);
        GatewaySessionCursor updated = cursor.grantAndDeliver(sequence, now);
        cursors.put(sessionId, updated);
        return updated;
    }

    @Override
    public synchronized GatewaySessionCursor acknowledge(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence
    ) {
        GatewaySessionCursor cursor = require(sessionId, userId);
        validateOwner(cursor, deliveryEpoch, ownerInstanceId);
        GatewaySessionCursor updated = cursor.acknowledge(sequence);
        cursors.put(sessionId, updated);
        return updated;
    }

    @Override
    public synchronized GatewaySessionCursor replaceEpoch(
        UUID sessionId,
        UUID userId,
        String ownerInstanceId,
        Instant leaseExpiresAt,
        Instant now
    ) {
        GatewaySessionCursor updated = require(sessionId, userId).nextEpoch(ownerInstanceId, leaseExpiresAt, now);
        cursors.put(sessionId, updated);
        return updated;
    }

    private GatewaySessionCursor require(UUID sessionId, UUID userId) {
        return find(sessionId, userId)
            .orElseThrow(() -> new GatewaySessionNotFoundException("gateway session cursor not found"));
    }

    private static void validateOwner(GatewaySessionCursor cursor, long deliveryEpoch, String ownerInstanceId) {
        if (cursor.deliveryEpoch() != deliveryEpoch) {
            throw new GatewayStaleDeliveryEpochException("gateway delivery epoch is stale");
        }
        if (!cursor.ownerInstanceId().equals(ownerInstanceId)) {
            throw new GatewayDeliveryOwnerMismatchException("gateway delivery owner mismatch");
        }
    }
}
