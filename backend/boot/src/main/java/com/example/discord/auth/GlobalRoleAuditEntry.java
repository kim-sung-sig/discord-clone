package com.example.discord.auth;

import java.time.Instant;
import java.util.UUID;

record GlobalRoleAuditEntry(
    UUID targetUserId,
    String role,
    GlobalRoleAuditAction action,
    String actor,
    GlobalRoleAuditResult result,
    Instant occurredAt
) {
    GlobalRoleAuditEntry {
        role = GlobalRole.canonical(role);
        actor = normalizeActor(actor);
    }

    private static String normalizeActor(String actor) {
        if (actor == null || actor.isBlank()) {
            return "admin-cli";
        }
        return actor.trim().length() > 128 ? actor.trim().substring(0, 128) : actor.trim();
    }
}
