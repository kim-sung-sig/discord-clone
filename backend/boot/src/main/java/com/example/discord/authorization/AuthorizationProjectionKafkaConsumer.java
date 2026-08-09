package com.example.discord.authorization;

import com.example.discord.permission.AuthorizationAudience;
import com.example.discord.permission.AuthzProjectionUpdated;
import com.example.discord.permission.AuthorizationProjectionConsumer;
import com.example.discord.permission.AuthorizationProjectionDeadLetter;
import com.example.discord.permission.AuthorizationProjectionEventEnvelope;
import com.example.discord.permission.AuthorizationWatermarkAdvanced;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
@Profile("postgres & kafka")
final class AuthorizationProjectionKafkaConsumer {
    private static final Logger log = LoggerFactory.getLogger(AuthorizationProjectionKafkaConsumer.class);
    private final ObjectMapper objectMapper;
    private final AuthorizationProjectionConsumer messageConsumer;
    private final AuthorizationProjectionConsumer gatewayConsumer;
    private final KafkaTemplate<String, String> deadLetterKafka;
    private final String topicPrefix;

    AuthorizationProjectionKafkaConsumer(ObjectMapper objectMapper, JdbcAuthorizationProjectionStore store) {
        this(objectMapper, store, null, "discord");
    }

    @Autowired
    AuthorizationProjectionKafkaConsumer(
        ObjectMapper objectMapper,
        JdbcAuthorizationProjectionStore store,
        KafkaTemplate<String, String> deadLetterKafka,
        @Value("${discord.kafka.topic-prefix:discord}") String topicPrefix
    ) {
        this.objectMapper = objectMapper;
        this.messageConsumer = new AuthorizationProjectionConsumer(store, AuthorizationAudience.MESSAGE);
        this.gatewayConsumer = new AuthorizationProjectionConsumer(store, AuthorizationAudience.GATEWAY);
        this.deadLetterKafka = deadLetterKafka;
        this.topicPrefix = topicPrefix == null || topicPrefix.isBlank() ? "discord" : topicPrefix.trim();
    }

    @KafkaListener(topics = "${discord.kafka.topic-prefix:discord}.authz.message.v1", groupId = "authz-projection-boot-message")
    void consumeMessage(String payload) { consume(payload, messageConsumer); }

    @KafkaListener(topics = "${discord.kafka.topic-prefix:discord}.authz.gateway.v1", groupId = "authz-projection-boot-gateway")
    void consumeGateway(String payload) { consume(payload, gatewayConsumer); }

    private void consume(String payload, AuthorizationProjectionConsumer consumer) {
        Object event;
        try {
            AuthorizationProjectionEventEnvelope envelope = objectMapper.readValue(payload, AuthorizationProjectionEventEnvelope.class);
            event = envelope.projectionUpdate() ? envelope.toProjectionUpdated() : envelope.toWatermarkAdvanced();
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            log.warn("Rejected malformed authorization projection event type={}", failure.getClass().getSimpleName());
            publishDeadLetter("MALFORMED_EVENT", payload, consumer);
            return;
        }
        if (event instanceof AuthzProjectionUpdated projection) consumer.consume(projection);
        else consumer.consume((AuthorizationWatermarkAdvanced) event);
    }

    private void publishDeadLetter(String reason, String payload, AuthorizationProjectionConsumer consumer) {
        if (deadLetterKafka == null) return;
        String audience = consumer == messageConsumer ? "message" : "gateway";
        try {
            String metadata = objectMapper.writeValueAsString(Map.of(
                "event", AuthorizationProjectionDeadLetter.from(reason, payload)));
            deadLetterKafka.send(topicPrefix + ".authz." + audience + ".v1.dead-letter", "malformed", metadata)
                .get(5, TimeUnit.SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("authorization dead-letter publish failed", failure);
        }
    }
}
