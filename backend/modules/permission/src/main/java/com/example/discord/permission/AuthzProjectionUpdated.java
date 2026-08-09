package com.example.discord.permission;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AuthzProjectionUpdated(
    UUID eventId,
    UUID guildId,
    UUID subjectId,
    AuthorizationResourceType resourceType,
    UUID resourceId,
    long permissionBits,
    long permissionVersion,
    AuthorizationAudience audience,
    Instant occurredAt,
    UUID correlationId
) {
    public AuthzProjectionUpdated {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        Objects.requireNonNull(audience, "audience must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        if (permissionVersion < 0) {
            throw new IllegalArgumentException("permissionVersion must not be negative");
        }
    }
}
