package com.example.discord.gateway;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record GatewaySession(
    UUID id,
    UUID userId,
    Set<UUID> guildIds,
    Instant lastAcknowledgedAt,
    boolean closed,
    long lastDeliveredSequence
) {
    public GatewaySession {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        guildIds = Set.copyOf(Objects.requireNonNull(guildIds, "guildIds must not be null"));
        Objects.requireNonNull(lastAcknowledgedAt, "lastAcknowledgedAt must not be null");
        if (lastDeliveredSequence < 0) {
            throw new IllegalArgumentException("lastDeliveredSequence must not be negative");
        }
    }

    GatewaySession withAck(Instant acknowledgedAt) {
        return new GatewaySession(id, userId, guildIds, acknowledgedAt, closed, lastDeliveredSequence);
    }

    GatewaySession close() {
        return new GatewaySession(id, userId, guildIds, lastAcknowledgedAt, true, lastDeliveredSequence);
    }

    GatewaySession withLastDeliveredSequence(long sequence) {
        return new GatewaySession(id, userId, guildIds, lastAcknowledgedAt, closed, sequence);
    }
}
