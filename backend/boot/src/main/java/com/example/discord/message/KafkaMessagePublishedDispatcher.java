package com.example.discord.message;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;

final class KafkaMessagePublishedDispatcher implements MessagePublishedDispatcher {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final MessageLookupPort messages;
    private final long publishTimeoutMillis;
    private final String topic;

    KafkaMessagePublishedDispatcher(
        KafkaTemplate<String, String> kafka,
        ObjectMapper objectMapper,
        MessageLookupPort messages,
        Clock clock,
        String topicPrefix,
        long publishTimeoutMillis
    ) {
        this.kafka = Objects.requireNonNull(kafka, "kafka must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        this.publishTimeoutMillis = Math.max(1L, publishTimeoutMillis);
        String prefix = topicPrefix == null || topicPrefix.isBlank() ? "discord" : topicPrefix.trim();
        this.topic = prefix + ".message.published.v1";
    }

    @Override
    public void dispatch(MessagePublished event) {
        if (!(event.target() instanceof ChannelMessageTarget channel)) {
            return;
        }
        messages.requireMessage(channel, event.messageId());
        MessagePublishedRecord record = MessagePublishedRecord.from(event, MessagePublishedRecord.payloadHashFor(event));
        try {
            kafka.send(topic, channel.channelId().toString(), encode(record))
                .get(publishTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("message publication Kafka publish interrupted", exception);
        } catch (ExecutionException | TimeoutException exception) {
            throw new IllegalStateException("message publication Kafka publish failed", exception);
        }
    }

    private String encode(MessagePublishedRecord record) {
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("message publication record is not serializable", exception);
        }
    }

}
