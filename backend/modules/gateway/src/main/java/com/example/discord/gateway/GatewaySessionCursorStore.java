package com.example.discord.gateway;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface GatewaySessionCursorStore {
    void create(GatewaySessionCursor cursor);

    Optional<GatewaySessionCursor> find(UUID sessionId, UUID userId);

    GatewaySessionCursor markDelivered(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence,
        Instant now
    );

    GatewaySessionCursor acknowledge(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence
    );

    default GatewaySessionCursor markDelivered(UUID sessionId, UUID userId, long sequence, Instant now) {
        GatewaySessionCursor current = find(sessionId, userId)
            .orElseThrow(() -> new GatewaySessionNotFoundException("gateway session cursor not found"));
        return markDelivered(sessionId, userId, current.deliveryEpoch(), current.ownerInstanceId(), sequence, now);
    }

    default GatewaySessionCursor acknowledge(UUID sessionId, UUID userId, long sequence) {
        GatewaySessionCursor current = find(sessionId, userId)
            .orElseThrow(() -> new GatewaySessionNotFoundException("gateway session cursor not found"));
        return acknowledge(sessionId, userId, current.deliveryEpoch(), current.ownerInstanceId(), sequence);
    }

    GatewaySessionCursor replaceEpoch(UUID sessionId, UUID userId, String ownerInstanceId, Instant leaseExpiresAt, Instant now);
}
