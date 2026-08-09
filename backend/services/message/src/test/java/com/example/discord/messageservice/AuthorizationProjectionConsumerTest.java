package com.example.discord.messageservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.permission.AuthzProjectionUpdated;
import com.example.discord.permission.AuthorizationAudience;
import com.example.discord.permission.AuthorizationDecision;
import com.example.discord.permission.AuthorizationProjection;
import com.example.discord.permission.AuthorizationProjectionConsumer;
import com.example.discord.permission.AuthorizationProjectionReducer;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.AuthorizationProjectionStore;
import com.example.discord.permission.AuthorizationWatermarkAdvanced;
import com.example.discord.permission.Permission;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationProjectionConsumerTest {
    @Test
    void duplicateAndOlderEventsDoNotOverwriteProjection() {
        FakeStore store = new FakeStore();
        AuthorizationProjectionConsumer consumer = new AuthorizationProjectionConsumer(store, AuthorizationAudience.MESSAGE);
        AuthzProjectionUpdated current = event(4L, Permission.SEND_MESSAGES.bit());
        AuthzProjectionUpdated older = new AuthzProjectionUpdated(
            UUID.randomUUID(), current.guildId(), current.subjectId(), current.resourceType(), current.resourceId(),
            0L, 3L, AuthorizationAudience.MESSAGE, Instant.now(), UUID.randomUUID());

        assertThat(consumer.consume(current).applied()).isTrue();
        assertThat(consumer.consume(current).applied()).isFalse();
        assertThat(consumer.consume(older).applied()).isFalse();
        consumer.consume(new AuthorizationWatermarkAdvanced(UUID.randomUUID(), current.guildId(), 4L, AuthorizationAudience.MESSAGE));
        assertThat(store.decide(current.guildId(), current.subjectId(), current.resourceType(), current.resourceId(), Permission.SEND_MESSAGES))
            .isEqualTo(AuthorizationDecision.allow());
    }

    @Test
    void missingWatermarkFailsClosed() {
        FakeStore store = new FakeStore();
        AuthorizationProjectionConsumer consumer = new AuthorizationProjectionConsumer(store, AuthorizationAudience.MESSAGE);
        AuthzProjectionUpdated event = event(1L, Permission.SEND_MESSAGES.bit());

        consumer.consume(event);

        assertThat(store.decide(event.guildId(), event.subjectId(), event.resourceType(), event.resourceId(), Permission.SEND_MESSAGES))
            .isEqualTo(AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION));
    }

    @Test
    void wrongAudienceIsIgnored() {
        FakeStore store = new FakeStore();
        AuthorizationProjectionConsumer consumer = new AuthorizationProjectionConsumer(store, AuthorizationAudience.MESSAGE);

        AuthzProjectionUpdated websocketEvent = new AuthzProjectionUpdated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            AuthorizationResourceType.CHANNEL, UUID.randomUUID(), Permission.SEND_MESSAGES.bit(), 1L, AuthorizationAudience.WEBSOCKET,
            Instant.now(), UUID.randomUUID());
        assertThat(consumer.consume(websocketEvent).applied())
            .isFalse();
    }

    private static AuthzProjectionUpdated event(long version, long bits) {
        return new AuthzProjectionUpdated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(), bits, version, AuthorizationAudience.MESSAGE, Instant.now(), UUID.randomUUID());
    }

    private static final class FakeStore implements AuthorizationProjectionStore {
        private AuthorizationProjection projection;
        private long watermark = -1;

        @Override
        public boolean apply(AuthzProjectionUpdated event) {
            AuthorizationProjectionReducer.Result result = AuthorizationProjectionReducer.apply(projection, event, Math.max(watermark, 0));
            if (result.applied()) {
                projection = result.projection();
            }
            return result.applied();
        }

        @Override
        public boolean advanceWatermark(AuthorizationWatermarkAdvanced event) {
            if (event.permissionVersion() <= watermark) return false;
            watermark = event.permissionVersion();
            if (projection != null) {
                projection = new AuthorizationProjection(
                    projection.guildId(), projection.subjectId(), projection.resourceType(), projection.resourceId(),
                    projection.permissionBits(), projection.permissionVersion(), watermark);
            }
            return true;
        }

        @Override
        public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType resourceType, UUID resourceId, Permission permission) {
            if (projection == null) return AuthorizationDecision.deny(AuthorizationDecision.Reason.MISSING_PROJECTION);
            if (watermark < 0) return AuthorizationDecision.deny(AuthorizationDecision.Reason.STALE_PROJECTION);
            return projection.decide(permission);
        }
    }
}
