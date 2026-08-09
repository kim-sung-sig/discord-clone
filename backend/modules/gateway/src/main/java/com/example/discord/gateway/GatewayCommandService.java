package com.example.discord.gateway;

import java.util.List;
import java.util.UUID;

/** Gateway Control application boundary used by HTTP/WebSocket adapters. */
public interface GatewayCommandService {
    GatewayIdentifyResult identify(UUID userId);

    GatewayHeartbeatResult heartbeat(UUID sessionId, UUID userId);

    GatewayResumeResult resume(UUID sessionId, UUID userId, long lastSequence);

    List<GatewayEvent> poll(UUID sessionId, UUID userId, long afterSequence);

    GatewaySessionCursor acknowledge(UUID sessionId, UUID userId, long sequence);

    GatewaySessionCursor acknowledge(
        UUID sessionId,
        UUID userId,
        long deliveryEpoch,
        String ownerInstanceId,
        long sequence
    );
}
