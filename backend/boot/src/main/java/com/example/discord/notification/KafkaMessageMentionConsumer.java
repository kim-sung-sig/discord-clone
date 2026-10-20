package com.example.discord.notification;

import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.MessagePublishedRecord;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("postgres & kafka")
final class KafkaMessageMentionConsumer {
    private final ObjectMapper mapper;
    private final MessageMentionProjector projector;

    KafkaMessageMentionConsumer(ObjectMapper mapper, MessageMentionProjector projector) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
        this.projector = Objects.requireNonNull(projector, "projector must not be null");
    }

    @KafkaListener(
        topics = "${discord.kafka.topic-prefix:discord}.message.published.v1",
        groupId = "notification-service-v1"
    )
    void consume(String payload) {
        MessagePublishedRecord record;
        try {
            record = mapper.readValue(payload, MessagePublishedRecord.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid message publication record", failure);
        }
        projector.project(new ChannelMessageTarget(record.guildId(), record.channelId()), record.messageId());
    }
}
