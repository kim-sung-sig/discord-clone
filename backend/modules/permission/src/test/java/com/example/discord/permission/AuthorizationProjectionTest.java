package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationProjectionTest {
    @Test
    void deniesWhenProjectionIsBehindWatermark() {
        AuthorizationProjection projection = new AuthorizationProjection(
            UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(), Permission.SEND_MESSAGES.bit(), 2L, 3L);

        assertThat(projection.decide(Permission.SEND_MESSAGES))
            .isEqualTo(AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION));
    }

    @Test
    void allowsPermissionWhenProjectionMatchesWatermark() {
        AuthorizationProjection projection = new AuthorizationProjection(
            UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(), Permission.SEND_MESSAGES.bit(), 3L, 3L);

        assertThat(projection.decide(Permission.SEND_MESSAGES)).isEqualTo(AuthorizationDecision.allow());
    }

    @Test
    void deniesUnlistedPermissionEvenWhenProjectionIsFresh() {
        AuthorizationProjection projection = new AuthorizationProjection(
            UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(), Permission.VIEW_CHANNEL.bit(), 3L, 3L);

        assertThat(projection.decide(Permission.SEND_MESSAGES))
            .isEqualTo(AuthorizationDecision.deny(AuthorizationDecision.Reason.PERMISSION_DENIED));
    }
}
