package com.example.discord.permission;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Wire-neutral envelope shared by the source outbox publisher and service consumers. */
public record AuthorizationProjectionEventEnvelope(
    String schema,
    String kind,
    UUID eventId,
    UUID guildId,
    UUID subjectId,
    AuthorizationResourceType resourceType,
    UUID resourceId,
    Long permissionBits,
    long permissionVersion,
    AuthorizationAudience audience,
    String source,
    Instant occurredAt,
    UUID correlationId
) {
    public static final String SCHEMA = "authz.projection.v1";
    public static final String PROJECTION_UPDATED = "PROJECTION_UPDATED";
    public static final String WATERMARK_ADVANCED = "WATERMARK_ADVANCED";

    public AuthorizationProjectionEventEnvelope {
        if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported authz schema");
        if (!PROJECTION_UPDATED.equals(kind) && !WATERMARK_ADVANCED.equals(kind)) {
            throw new IllegalArgumentException("unsupported authz event kind");
        }
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(audience, "audience must not be null");
        if (permissionVersion < 0) throw new IllegalArgumentException("permissionVersion must not be negative");
        if (!"community".equals(source)) throw new IllegalArgumentException("unsupported authz event source");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        if (PROJECTION_UPDATED.equals(kind)) {
            Objects.requireNonNull(subjectId, "subjectId must not be null");
            Objects.requireNonNull(resourceType, "resourceType must not be null");
            Objects.requireNonNull(resourceId, "resourceId must not be null");
            Objects.requireNonNull(permissionBits, "permissionBits must not be null");
            if (permissionBits < 0) throw new IllegalArgumentException("permissionBits must not be negative");
            Objects.requireNonNull(correlationId, "correlationId must not be null");
        } else if (subjectId != null || resourceType != null || resourceId != null || permissionBits != null || correlationId != null) {
            throw new IllegalArgumentException("watermark event must not contain projection fields");
        }
    }

    public static AuthorizationProjectionEventEnvelope projection(AuthzProjectionUpdated event) {
        Objects.requireNonNull(event, "event must not be null");
        return new AuthorizationProjectionEventEnvelope(
            SCHEMA, PROJECTION_UPDATED, event.eventId(), event.guildId(), event.subjectId(), event.resourceType(),
            event.resourceId(), event.permissionBits(), event.permissionVersion(), event.audience(), "community",
            event.occurredAt(), event.correlationId());
    }

    public static AuthorizationProjectionEventEnvelope watermark(AuthorizationWatermarkAdvanced event, Instant occurredAt) {
        Objects.requireNonNull(event, "event must not be null");
        return new AuthorizationProjectionEventEnvelope(
            SCHEMA, WATERMARK_ADVANCED, event.eventId(), event.guildId(), null, null, null, null,
            event.permissionVersion(), event.audience(), "community", Objects.requireNonNull(occurredAt), null);
    }

    public boolean projectionUpdate() { return PROJECTION_UPDATED.equals(kind); }

    public AuthzProjectionUpdated toProjectionUpdated() {
        if (!projectionUpdate()) throw new IllegalStateException("event is not a projection update");
        return new AuthzProjectionUpdated(eventId, guildId, subjectId, resourceType, resourceId, permissionBits,
            permissionVersion, audience, occurredAt, correlationId);
    }

    public AuthorizationWatermarkAdvanced toWatermarkAdvanced() {
        if (projectionUpdate()) throw new IllegalStateException("event is not a watermark update");
        return new AuthorizationWatermarkAdvanced(eventId, guildId, permissionVersion, audience);
    }
}
