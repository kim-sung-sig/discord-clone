package com.example.discord.messageservice;

import com.example.discord.message.MessagePublished;
import com.example.discord.message.MessagePublishedDispatcher;
import com.example.discord.message.MessagePublishedRecord;
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
    private final long timeoutMillis;
    private final String topic;

    KafkaMessagePublishedDispatcher(
        KafkaTemplate<String, String> kafka,
        ObjectMapper objectMapper,
        Clock clock,
        String topicPrefix,
        long timeoutMillis
    ) {
        this.kafka = Objects.requireNonNull(kafka, "kafka must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(clock, "clock must not be null");
        this.timeoutMillis = Math.max(1L, timeoutMillis);
        this.topic = (topicPrefix == null || topicPrefix.isBlank() ? "discord" : topicPrefix.trim())
            + ".message.published.v1";
    }

    @Override
    public void dispatch(MessagePublished event) {
        if (!(event.target() instanceof com.example.discord.message.ChannelMessageTarget channel)) return;
        MessagePublishedRecord record = MessagePublishedRecord.from(event, MessagePublishedRecord.payloadHashFor(event));
        try {
            kafka.send(topic, channel.channelId().toString(), objectMapper.writeValueAsString(record))
                .get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("message publication Kafka publish interrupted", exception);
        } catch (ExecutionException | TimeoutException | JsonProcessingException exception) {
            throw new IllegalStateException("message publication Kafka publish failed", exception);
        }
    }

}
