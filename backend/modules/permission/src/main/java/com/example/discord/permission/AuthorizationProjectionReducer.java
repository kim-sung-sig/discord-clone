package com.example.discord.permission;

import java.util.Objects;

public final class AuthorizationProjectionReducer {
    private AuthorizationProjectionReducer() {}

    public static Result apply(
        AuthorizationProjection current,
        AuthzProjectionUpdated event,
        long watermarkVersion
    ) {
        Objects.requireNonNull(event, "event must not be null");
        if (watermarkVersion < 0) {
            throw new IllegalArgumentException("watermarkVersion must not be negative");
        }
        if (current != null) {
            if (!current.guildId().equals(event.guildId())
                || !current.subjectId().equals(event.subjectId())
                || current.resourceType() != event.resourceType()
                || !current.resourceId().equals(event.resourceId())) {
                throw new IllegalArgumentException("projection key does not match event");
            }
            if (event.permissionVersion() <= current.permissionVersion()) {
                return new Result(current, false);
            }
        }
        return new Result(new AuthorizationProjection(
            event.guildId(),
            event.subjectId(),
            event.resourceType(),
            event.resourceId(),
            event.permissionBits(),
            event.permissionVersion(),
            watermarkVersion
        ), true);
    }

    public record Result(AuthorizationProjection projection, boolean applied) {
        public Result {
            Objects.requireNonNull(projection, "projection must not be null");
        }
    }
}
