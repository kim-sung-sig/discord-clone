package com.example.discord.message;

import com.example.discord.gateway.InMemoryGatewayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
final class KafkaMessagePublishedConsumer {
    private final ObjectMapper objectMapper;
    private final MessageLookupPort messages;
    private final InMemoryGatewayService gateway;
    private final MessagePublicationInbox inbox;

    KafkaMessagePublishedConsumer(
        ObjectMapper objectMapper,
        MessageLookupPort messages,
        InMemoryGatewayService gateway,
        MessagePublicationInbox inbox
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.inbox = Objects.requireNonNull(inbox, "inbox must not be null");
    }

    @KafkaListener(
        topics = "${discord.kafka.topic-prefix:discord}.message.published.v1",
        groupId = "gateway-service-v1"
    )
    void consume(String payload) {
        MessagePublishedRecord record = decode(payload);
        if (!inbox.claim(record.eventId())) {
            return;
        }
        try {
            ChannelMessageTarget target = new ChannelMessageTarget(record.guildId(), record.channelId());
            Message message = messages.requireMessage(target, record.messageId());
            gateway.publish(
                record.eventId(),
                "MESSAGE_CREATE",
                record.guildId(),
                record.channelId(),
                gatewayPayload(message)
            );
        } catch (RuntimeException failure) {
            inbox.release(record.eventId());
            throw failure;
        }
    }

    private MessagePublishedRecord decode(String payload) {
        try {
            return objectMapper.readValue(payload, MessagePublishedRecord.class);
        } catch (JsonProcessingException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid message publication record", failure);
        }
    }

    private static Map<String, Object> gatewayPayload(Message message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", message.id().toString());
        payload.put("guildId", message.guildId().toString());
        payload.put("channelId", message.channelId().toString());
        payload.put("authorId", message.authorId().toString());
        payload.put("content", message.content().value());
        payload.put("mentions", message.mentions().stream().map(KafkaMessagePublishedConsumer::mentionToken).toList());
        payload.put("pinned", message.pinned());
        payload.put("deleted", message.deleted());
        payload.put("edited", message.edited());
        payload.put("createdAt", message.createdAt().toString());
        payload.put("updatedAt", message.updatedAt().toString());
        return payload;
    }

    private static String mentionToken(MessageMentionTarget mention) {
        return switch (mention) {
            case UserMentionTarget user -> user.userId().toString();
            case RoleMentionTarget role -> role.roleId().toString();
            case ChannelMentionTarget channel -> channel.channelId().toString();
            case SpecialMentionTarget special -> special.kind().name().toLowerCase(Locale.ROOT);
        };
    }
}
