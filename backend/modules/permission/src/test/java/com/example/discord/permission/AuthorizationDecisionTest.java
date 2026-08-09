package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizationDecisionTest {
    @Test
    void staleProjectionIsDenied() {
        AuthorizationDecision decision = AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).isEqualTo(AuthorizationDecision.Reason.STALE_PROJECTION);
    }

    @Test
    void allowedDecisionIsExplicit() {
        AuthorizationDecision decision = AuthorizationDecision.allow();

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo(AuthorizationDecision.Reason.ALLOWED);
    }
}
