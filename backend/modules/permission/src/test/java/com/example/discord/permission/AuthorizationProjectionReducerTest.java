package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationProjectionReducerTest {
    @Test
    void ignoresDuplicateAndOlderVersions() {
        UUID guildId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        AuthorizationProjection current = new AuthorizationProjection(
            guildId, subjectId, AuthorizationResourceType.CHANNEL, resourceId,
            Permission.SEND_MESSAGES.bit(), 4L, 4L);
        AuthzProjectionUpdated older = event(guildId, subjectId, resourceId, 3L, 0L);

        AuthorizationProjectionReducer.Result result = AuthorizationProjectionReducer.apply(current, older, 4L);

        assertThat(result.applied()).isFalse();
        assertThat(result.projection()).isSameAs(current);
    }

    @Test
    void appliesNewVersionButKeepsBehindWatermarkStale() {
        UUID guildId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        AuthzProjectionUpdated newer = event(guildId, subjectId, resourceId, 5L, Permission.SEND_MESSAGES.bit());

        AuthorizationProjectionReducer.Result result = AuthorizationProjectionReducer.apply(null, newer, 4L);

        assertThat(result.applied()).isTrue();
        assertThat(result.projection().permissionVersion()).isEqualTo(5L);
        assertThat(result.projection().decide(Permission.SEND_MESSAGES))
            .isEqualTo(AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION));
    }

    private static AuthzProjectionUpdated event(
        UUID guildId,
        UUID subjectId,
        UUID resourceId,
        long version,
        long permissionBits
    ) {
        return new AuthzProjectionUpdated(
            UUID.randomUUID(), guildId, subjectId, AuthorizationResourceType.CHANNEL, resourceId,
            permissionBits, version, AuthorizationAudience.MESSAGE, Instant.parse("2026-08-09T00:00:00Z"),
            UUID.randomUUID());
    }
}
