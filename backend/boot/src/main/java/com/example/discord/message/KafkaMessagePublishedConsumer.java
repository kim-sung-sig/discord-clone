package com.example.discord.message;

import com.example.discord.gateway.GatewayEventConflictException;
import com.example.discord.gateway.InMemoryGatewayService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
final class KafkaMessagePublishedConsumer {
    private static final Logger log = LoggerFactory.getLogger(KafkaMessagePublishedConsumer.class);

    private final ObjectMapper objectMapper;
    private final MessageLookupPort messages;
    private final InMemoryGatewayService gateway;
    private final MessagePublicationInbox inbox;
    private final MeterRegistry metrics;

    KafkaMessagePublishedConsumer(
        ObjectMapper objectMapper,
        MessageLookupPort messages,
        InMemoryGatewayService gateway,
        MessagePublicationInbox inbox,
        MeterRegistry metrics
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.inbox = Objects.requireNonNull(inbox, "inbox must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    @KafkaListener(
        topics = "${discord.kafka.topic-prefix:discord}.message.published.v1",
        groupId = "gateway-service-v1"
    )
    void consume(String payload) {
        MessagePublishedRecord record = null;
        try {
            record = decode(payload);
            if (inbox.isProcessed(record.eventId())) {
                count("duplicate");
                log.debug("Skipped duplicate message publication eventId={} correlationId={}",
                    record.eventId(), record.correlationId());
                return;
            }
            // The Gateway side effect may already exist when inbox completion failed after the append. Re-publishing
            // would rebuild the payload from the current (possibly edited) message and conflict with the logged event.
            if (gateway.hasAppended(record.eventId())) {
                log.info("Gateway event already appended; completing inbox eventId={} correlationId={}",
                    record.eventId(), record.correlationId());
            } else {
                publishToGateway(record);
            }
            inbox.markProcessed(record.eventId());
            count("processed");
            Duration endToEnd = Duration.between(record.occurredAt(), Instant.now());
            metrics.timer("discord.message.publication.end_to_end")
                .record(endToEnd.isNegative() ? Duration.ZERO : endToEnd);
            log.info("Message publication consumed eventId={} correlationId={}", record.eventId(), record.correlationId());
        } catch (RuntimeException failure) {
            count("failed");
            if (record == null) {
                log.warn("Invalid message publication record reason={}", failure.getClass().getSimpleName());
            } else {
                log.warn("Message publication failed eventId={} correlationId={} reason={}",
                    record.eventId(), record.correlationId(), failure.getClass().getSimpleName());
            }
            throw failure;
        }
    }

    private void publishToGateway(MessagePublishedRecord record) {
        ChannelMessageTarget target = new ChannelMessageTarget(record.guildId(), record.channelId());
        Message message = messages.requireMessage(target, record.messageId());
        try {
            gateway.publish(
                record.eventId(),
                "MESSAGE_CREATE",
                record.guildId(),
                record.channelId(),
                gatewayPayload(message)
            );
        } catch (GatewayEventConflictException alreadyAppended) {
            // A concurrent delivery appended this event ID first from an older message state.
            log.info("Gateway event appended concurrently; completing inbox eventId={} correlationId={}",
                record.eventId(), record.correlationId());
        }
    }

    private void count(String outcome) {
        metrics.counter("discord.message.publication.consumer.events", "outcome", outcome).increment();
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
