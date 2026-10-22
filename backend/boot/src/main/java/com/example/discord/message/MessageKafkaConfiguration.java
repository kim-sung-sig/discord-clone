package com.example.discord.message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@Profile("kafka")
class MessageKafkaConfiguration {
    @Bean
    AdminClient messagePublicationLagAdminClient(KafkaAdmin kafkaAdmin) {
        return AdminClient.create(kafkaAdmin.getConfigurationProperties());
    }

    @Bean
    MessagePublicationConsumerLag messagePublicationConsumerLag(
        AdminClient messagePublicationLagAdminClient,
        MeterRegistry metrics,
        @Value("${discord.kafka.topic-prefix:discord}") String topicPrefix
    ) {
        return new MessagePublicationConsumerLag(messagePublicationLagAdminClient, metrics, topicPrefix);
    }

    @Bean
    @Profile("test & !postgres")
    MessagePublicationInbox messagePublicationInbox() {
        return new InMemoryMessagePublicationInbox();
    }

    @Bean
    CommonErrorHandler messageKafkaErrorHandler(
        KafkaTemplate<String, String> kafka,
        ObjectMapper objectMapper,
        MeterRegistry metrics,
        @Value("${discord.kafka.topic-prefix:discord}") String topicPrefix,
        @Value("${discord.kafka.message-dlq-timeout-ms:5000}") long dlqTimeoutMillis
    ) {
        String dlqTopic = (topicPrefix == null || topicPrefix.isBlank() ? "discord" : topicPrefix.trim())
            + ".message.published.v1.dead-letter";
        String messageTopic = (topicPrefix == null || topicPrefix.isBlank() ? "discord" : topicPrefix.trim())
            + ".message.published.v1";
        ConsumerRecordRecoverer recoverer = (record, failure) -> {
            try {
                kafka.send(
                    dlqTopic,
                    String.valueOf(record.key()),
                    deadLetter(objectMapper, record, failure)
                ).get(Math.max(1L, dlqTimeoutMillis), TimeUnit.MILLISECONDS);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("message publication dead-letter publish interrupted", exception);
            } catch (Exception exception) {
                throw new IllegalStateException("message publication dead-letter publish failed", exception);
            }
            if (messageTopic.equals(record.topic())) {
                metrics.counter("discord.message.publication.consumer.events", "outcome", "dead_letter").increment();
            }
        };
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000L, 2L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setRetryListeners((RetryListener) (record, failure, deliveryAttempt) -> {
            if (messageTopic.equals(record.topic())) {
                metrics.counter("discord.message.publication.consumer.retries").increment();
            }
        });
        return handler;
    }

    static String deadLetter(ObjectMapper mapper, ConsumerRecord<?, ?> record, Exception failure) {
        try {
            JsonNode json = null;
            try {
                json = record.value() == null ? null : mapper.readTree(record.value().toString());
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalidPayload) {
                // Poison records still need metadata-only recovery when their JSON cannot be parsed.
            }
            return mapper.writeValueAsString(Map.of(
                "schema", "discord.message.published.dead-letter.v1",
                "reason", failure.getClass().getSimpleName(),
                "eventId", json != null && json.has("eventId") ? json.get("eventId").asText() : "",
                "topic", record.topic(),
                "size", record.value() == null ? 0 : record.value().toString().length(),
                "payloadSha256Prefix", sha256Prefix(record.value() == null ? "" : record.value().toString())
            ));
        } catch (Exception encodingFailure) {
            throw new IllegalStateException("message publication dead-letter encoding failed", encodingFailure);
        }
    }

    private static String sha256Prefix(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
