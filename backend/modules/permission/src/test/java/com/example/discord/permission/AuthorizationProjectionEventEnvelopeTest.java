package com.example.discord.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationProjectionEventEnvelopeTest {
    @Test
    void preservesProjectionEventIdentityAndAudience() {
        AuthzProjectionUpdated source = new AuthzProjectionUpdated(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(), Permission.SEND_MESSAGES.bit(), 7L, AuthorizationAudience.MESSAGE,
            Instant.parse("2026-08-09T00:00:00Z"), UUID.randomUUID());

        AuthzProjectionUpdated decoded = AuthorizationProjectionEventEnvelope.projection(source).toProjectionUpdated();

        assertThat(decoded).isEqualTo(source);
    }

    @Test
    void watermarkEnvelopeContainsNoProjectionPayload() {
        AuthorizationWatermarkAdvanced source = new AuthorizationWatermarkAdvanced(
            UUID.randomUUID(), UUID.randomUUID(), 7L, AuthorizationAudience.WEBSOCKET);

        AuthorizationProjectionEventEnvelope envelope = AuthorizationProjectionEventEnvelope.watermark(source, Instant.now());

        assertThat(envelope.projectionUpdate()).isFalse();
        assertThat(envelope.toWatermarkAdvanced()).isEqualTo(source);
    }
}
