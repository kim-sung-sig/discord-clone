package com.example.discord.messageservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.permission.AuthzProjectionUpdated;
import com.example.discord.permission.AuthorizationAudience;
import com.example.discord.permission.AuthorizationDecision;
import com.example.discord.permission.AuthorizationProjection;
import com.example.discord.permission.AuthorizationProjectionEventEnvelope;
import com.example.discord.permission.AuthorizationProjectionReducer;
import com.example.discord.permission.AuthorizationProjectionStore;
import com.example.discord.permission.AuthorizationResourceType;
import com.example.discord.permission.AuthorizationWatermarkAdvanced;
import com.example.discord.permission.Permission;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuthorizationProjectionKafkaConsumerTest {
    @Test
    void consumesProjectionThenWatermarkAndAllowsLocalDecision() throws Exception {
        Store store = new Store();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AuthorizationProjectionKafkaConsumer consumer = new AuthorizationProjectionKafkaConsumer(mapper, store);
        AuthzProjectionUpdated projection = event();
        consumer.consume(mapper.writeValueAsString(AuthorizationProjectionEventEnvelope.projection(projection)));
        consumer.consume(mapper.writeValueAsString(AuthorizationProjectionEventEnvelope.watermark(
            new AuthorizationWatermarkAdvanced(UUID.randomUUID(), projection.guildId(), 3L, AuthorizationAudience.MESSAGE), projection.occurredAt())));
        assertThat(store.decide(projection.guildId(), projection.subjectId(), projection.resourceType(), projection.resourceId(), Permission.SEND_MESSAGES))
            .isEqualTo(AuthorizationDecision.allow());
    }

    @Test
    void malformedPayloadDoesNotMutateProjection() {
        Store store = new Store();
        new AuthorizationProjectionKafkaConsumer(new ObjectMapper().findAndRegisterModules(), store).consume("not-json");
        assertThat(store.applyCount).isZero();
    }

    @Test
    void storageFailureIsPropagatedForKafkaRetry() throws Exception {
        AuthzProjectionUpdated projection = event();
        AuthorizationProjectionStore failing = new AuthorizationProjectionStore() {
            public boolean apply(AuthzProjectionUpdated ignored) { throw new IllegalStateException("database unavailable"); }
            public boolean advanceWatermark(AuthorizationWatermarkAdvanced ignored) { throw new IllegalStateException("database unavailable"); }
            public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType type, UUID resourceId, Permission permission) {
                return AuthorizationDecision.deny(AuthorizationDecision.Reason.MISSING_PROJECTION);
            }
        };
        AuthorizationProjectionKafkaConsumer consumer = new AuthorizationProjectionKafkaConsumer(
            new ObjectMapper().findAndRegisterModules(), failing);

        assertThatThrownBy(() -> consumer.consume(new ObjectMapper().findAndRegisterModules()
            .writeValueAsString(AuthorizationProjectionEventEnvelope.projection(projection))))
            .isInstanceOf(IllegalStateException.class);
    }

    private static AuthzProjectionUpdated event() {
        return new AuthzProjectionUpdated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(), Permission.SEND_MESSAGES.bit(), 3L, AuthorizationAudience.MESSAGE, Instant.now(), UUID.randomUUID());
    }

    private static final class Store implements AuthorizationProjectionStore {
        private AuthorizationProjection projection;
        private long watermark = -1;
        private int applyCount;
        public boolean apply(AuthzProjectionUpdated event) {
            applyCount++;
            AuthorizationProjectionReducer.Result result = AuthorizationProjectionReducer.apply(projection, event, Math.max(watermark, 0));
            if (result.applied()) projection = result.projection();
            return result.applied();
        }
        public boolean advanceWatermark(AuthorizationWatermarkAdvanced event) {
            if (event.permissionVersion() <= watermark) return false;
            watermark = event.permissionVersion();
            projection = new AuthorizationProjection(projection.guildId(), projection.subjectId(), projection.resourceType(), projection.resourceId(),
                projection.permissionBits(), projection.permissionVersion(), watermark);
            return true;
        }
        public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType type, UUID resourceId, Permission permission) {
            return projection == null ? AuthorizationDecision.deny(AuthorizationDecision.Reason.MISSING_PROJECTION) : projection.decide(permission);
        }
    }
}
