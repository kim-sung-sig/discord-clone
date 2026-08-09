package com.example.discord.permission;

import java.util.UUID;

public interface AuthorizationProjectionStore {
    boolean apply(AuthzProjectionUpdated event);
    boolean advanceWatermark(AuthorizationWatermarkAdvanced event);
    AuthorizationDecision decide(
        UUID guildId,
        UUID subjectId,
        AuthorizationResourceType resourceType,
        UUID resourceId,
        Permission permission
    );
}
