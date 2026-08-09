package com.example.discord.websocketservice;

import static org.assertj.core.api.Assertions.assertThat;

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
    void appliesOnlyWebsocketAudienceAndUsesWatermarkForDecision() throws Exception {
        Store store = new Store();
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        AuthorizationProjectionKafkaConsumer consumer = new AuthorizationProjectionKafkaConsumer(mapper, store);
        AuthzProjectionUpdated projection = new AuthzProjectionUpdated(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), AuthorizationResourceType.CHANNEL,
            UUID.randomUUID(), Permission.VIEW_CHANNEL.bit(), 2L, AuthorizationAudience.WEBSOCKET, Instant.now(), UUID.randomUUID());
        consumer.consume(mapper.writeValueAsString(AuthorizationProjectionEventEnvelope.projection(projection)));
        consumer.consume(mapper.writeValueAsString(AuthorizationProjectionEventEnvelope.watermark(
            new AuthorizationWatermarkAdvanced(UUID.randomUUID(), projection.guildId(), 2L, AuthorizationAudience.WEBSOCKET), projection.occurredAt())));
        assertThat(store.decide(projection.guildId(), projection.subjectId(), projection.resourceType(), projection.resourceId(), Permission.VIEW_CHANNEL))
            .isEqualTo(AuthorizationDecision.allow());
    }

    private static final class Store implements AuthorizationProjectionStore {
        private AuthorizationProjection projection;
        private long watermark = -1;
        public boolean apply(AuthzProjectionUpdated event) {
            AuthorizationProjectionReducer.Result result = AuthorizationProjectionReducer.apply(projection, event, Math.max(watermark, 0));
            if (result.applied()) projection = result.projection();
            return result.applied();
        }
        public boolean advanceWatermark(AuthorizationWatermarkAdvanced event) {
            watermark = Math.max(watermark, event.permissionVersion());
            projection = new AuthorizationProjection(projection.guildId(), projection.subjectId(), projection.resourceType(), projection.resourceId(),
                projection.permissionBits(), projection.permissionVersion(), watermark);
            return true;
        }
        public AuthorizationDecision decide(UUID guildId, UUID subjectId, AuthorizationResourceType type, UUID resourceId, Permission permission) {
            return projection == null ? AuthorizationDecision.deny(AuthorizationDecision.Reason.MISSING_PROJECTION) : projection.decide(permission);
        }
    }
}
