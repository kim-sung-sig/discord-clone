package com.example.discord.permission;

import java.util.Objects;
import java.util.UUID;

public record AuthorizationProjection(
    UUID guildId,
    UUID subjectId,
    AuthorizationResourceType resourceType,
    UUID resourceId,
    long permissionBits,
    long permissionVersion,
    long watermarkVersion
) {
    public AuthorizationProjection {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(subjectId, "subjectId must not be null");
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        Objects.requireNonNull(resourceId, "resourceId must not be null");
        if (permissionVersion < 0 || watermarkVersion < 0) {
            throw new IllegalArgumentException("projection versions must not be negative");
        }
    }

    public AuthorizationDecision decide(Permission permission) {
        Objects.requireNonNull(permission, "permission must not be null");
        if (permissionVersion != watermarkVersion) {
            return AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION);
        }
        boolean allowed = (permissionBits & Permission.ADMINISTRATOR.bit()) != 0
            || (permissionBits & permission.bit()) != 0;
        return allowed
            ? AuthorizationDecision.allow()
            : AuthorizationDecision.deny(AuthorizationDecision.Reason.PERMISSION_DENIED);
    }
}
