package com.example.discord.gateway;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record GatewaySessionCursor(
    UUID sessionId,
    UUID userId,
    long acknowledgedUserSequence,
    long highestGrantedUserSequence,
    long highestDeliveredUserSequence,
    long deliveryEpoch,
    String ownerInstanceId,
    Instant ownerLeaseExpiresAt,
    Instant updatedAt
) {
    public GatewaySessionCursor {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (acknowledgedUserSequence < 0
            || highestDeliveredUserSequence < acknowledgedUserSequence
            || highestGrantedUserSequence < highestDeliveredUserSequence) {
            throw new IllegalArgumentException("invalid gateway cursor monotonicity");
        }
        if (deliveryEpoch < 1) {
            throw new IllegalArgumentException("deliveryEpoch must be positive");
        }
        if (ownerInstanceId == null || ownerInstanceId.isBlank()) {
            throw new IllegalArgumentException("ownerInstanceId is required");
        }
        Objects.requireNonNull(ownerLeaseExpiresAt, "ownerLeaseExpiresAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    GatewaySessionCursor acknowledge(long sequence) {
        if (sequence > highestDeliveredUserSequence) {
            throw new GatewayAckOutOfRangeException("acknowledged sequence exceeds delivered sequence");
        }
        return new GatewaySessionCursor(
            sessionId, userId, Math.max(acknowledgedUserSequence, sequence), highestGrantedUserSequence,
            highestDeliveredUserSequence, deliveryEpoch, ownerInstanceId, ownerLeaseExpiresAt, updatedAt
        );
    }

    GatewaySessionCursor grantAndDeliver(long sequence, Instant now) {
        long granted = Math.max(highestGrantedUserSequence, sequence);
        return new GatewaySessionCursor(
            sessionId, userId, acknowledgedUserSequence, granted, Math.max(highestDeliveredUserSequence, sequence),
            deliveryEpoch, ownerInstanceId, ownerLeaseExpiresAt, now
        );
    }

    GatewaySessionCursor nextEpoch(String owner, Instant leaseExpiresAt, Instant now) {
        return new GatewaySessionCursor(
            sessionId, userId, acknowledgedUserSequence, acknowledgedUserSequence, acknowledgedUserSequence,
            deliveryEpoch + 1L, owner, leaseExpiresAt, now
        );
    }
}
