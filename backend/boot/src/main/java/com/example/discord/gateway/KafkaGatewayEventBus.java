package com.example.discord.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("kafka")
@EnableKafka
final class KafkaGatewayEventBus implements GatewayEventBus {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final String nodeId;
    private final String topic;
    private final String deadLetterTopic;
    private final int deadLetterAlertThreshold;
    private final AtomicLong deadLetterTotal = new AtomicLong();
    private final ConcurrentMap<String, AtomicLong> deadLettersByReason = new ConcurrentHashMap<>();
    private final List<Consumer<GatewayBusEvent>> listeners = new CopyOnWriteArrayList<>();

    KafkaGatewayEventBus(
        KafkaTemplate<String, String> kafka,
        ObjectMapper objectMapper,
        MeterRegistry meterRegistry,
        Clock clock,
        @Value("${discord.gateway.node-id:${random.uuid}}") String nodeId,
        @Value("${discord.kafka.topic-prefix:discord}") String topicPrefix,
        @Value("${discord.kafka.gateway-dlq-alert-threshold:1}") int deadLetterAlertThreshold
    ) {
        this.kafka = Objects.requireNonNull(kafka, "kafka must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.deadLetterAlertThreshold = Math.max(0, deadLetterAlertThreshold);
        String prefix = Objects.requireNonNull(topicPrefix, "topicPrefix must not be null").trim();
        this.topic = (prefix.isEmpty() ? "discord" : prefix) + ".gateway.events";
        this.deadLetterTopic = this.topic + ".dead-letter";
    }

    @Override
    public GatewayBusEvent publish(GatewayBusPublishCommand command) {
        GatewayBusEvent event = new GatewayBusEvent(
            UUID.randomUUID().toString(),
            command.type(),
            command.guildId(),
            command.channelId(),
            command.payload(),
            clock.instant()
        );
        kafka.send(topic, event.guildId().toString(), encode(new KafkaGatewayEnvelope(nodeId, event)));
        notifyListeners(event);
        return event;
    }

    @Override
    public void addEventListener(Consumer<GatewayBusEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    KafkaGatewayDeadLetterMetrics deadLetterMetrics() {
        long total = deadLetterTotal.get();
        Map<String, Long> byReason = deadLettersByReason.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
        return new KafkaGatewayDeadLetterMetrics(total, byReason, deadLetterAlert(total));
    }

    @KafkaListener(
        topics = "${discord.kafka.topic-prefix:discord}.gateway.events",
        groupId = "${discord.gateway.node-id:${random.uuid}}"
    )
    void handleMessage(String message) {
        Optional<KafkaGatewayEnvelope> decoded = decode(message);
        if (decoded.isEmpty()) {
            publishDeadLetter("MALFORMED_MESSAGE", message, null, null);
            return;
        }

        KafkaGatewayEnvelope envelope = decoded.get();
        if (nodeId.equals(envelope.sourceNodeId())) {
            return;
        }

        GatewayBusEvent event;
        try {
            event = envelope.event();
        } catch (RuntimeException exception) {
            publishDeadLetter("INVALID_ENVELOPE", message, envelope, null);
            return;
        }

        notifyRemoteListeners(event, message, envelope);
    }

    private String encode(KafkaGatewayEnvelope envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("gateway payload is not serializable", exception);
        }
    }

    private Optional<KafkaGatewayEnvelope> decode(String message) {
        try {
            return Optional.of(objectMapper.readValue(message, KafkaGatewayEnvelope.class));
        } catch (RuntimeException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private void notifyListeners(GatewayBusEvent event) {
        for (Consumer<GatewayBusEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    private void notifyRemoteListeners(
        GatewayBusEvent event,
        String message,
        KafkaGatewayEnvelope envelope
    ) {
        for (Consumer<GatewayBusEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                publishDeadLetter("LISTENER_FAILURE", message, envelope, event);
            }
        }
    }

    private void publishDeadLetter(
        String reason,
        String message,
        KafkaGatewayEnvelope envelope,
        GatewayBusEvent event
    ) {
        recordDeadLetterMetrics(reason);
        KafkaGatewayDeadLetter deadLetter = new KafkaGatewayDeadLetter(
            reason,
            nodeId,
            envelope == null ? null : envelope.sourceNodeId(),
            event == null ? null : event.eventId(),
            event == null ? null : event.type(),
            clock.instant().toString(),
            message == null ? 0 : message.length(),
            sha256Prefix(message)
        );
        kafka.send(deadLetterTopic, nodeId, encode(deadLetter));
    }

    private void recordDeadLetterMetrics(String reason) {
        deadLetterTotal.incrementAndGet();
        deadLettersByReason.computeIfAbsent(reason, ignored -> new AtomicLong()).incrementAndGet();
        Counter.builder("discord.gateway.kafka.dlq.records")
            .description("Kafka Gateway dead-letter records")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();
    }

    private KafkaGatewayDeadLetterAlert deadLetterAlert(long total) {
        boolean active = deadLetterAlertThreshold > 0 && total >= deadLetterAlertThreshold;
        String reason = active
            ? "DLQ count %d reached threshold %d".formatted(total, deadLetterAlertThreshold)
            : "";
        return new KafkaGatewayDeadLetterAlert(active, deadLetterAlertThreshold, reason);
    }

    private String encode(KafkaGatewayDeadLetter deadLetter) {
        try {
            return objectMapper.writeValueAsString(deadLetter);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("gateway dead-letter payload is not serializable", exception);
        }
    }

    private static String sha256Prefix(String message) {
        if (message == null) {
            return "";
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(message.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record KafkaGatewayEnvelope(
        String sourceNodeId,
        String eventId,
        String type,
        UUID guildId,
        UUID channelId,
        Map<String, Object> payload,
        String createdAt
    ) {
        KafkaGatewayEnvelope(String sourceNodeId, GatewayBusEvent event) {
            this(
                sourceNodeId,
                event.eventId(),
                event.type(),
                event.guildId(),
                event.channelId(),
                event.payload(),
                event.createdAt().toString()
            );
        }

        GatewayBusEvent event() {
            return new GatewayBusEvent(
                Objects.requireNonNull(eventId, "eventId must not be null"),
                Objects.requireNonNull(type, "type must not be null"),
                Objects.requireNonNull(guildId, "guildId must not be null"),
                channelId,
                Objects.requireNonNull(payload, "payload must not be null"),
                Instant.parse(Objects.requireNonNull(createdAt, "createdAt must not be null"))
            );
        }
    }

    private record KafkaGatewayDeadLetter(
        String reason,
        String handlingNodeId,
        String sourceNodeId,
        String eventId,
        String type,
        String receivedAt,
        int messageSize,
        String messageSha256Prefix
    ) {
    }

    record KafkaGatewayDeadLetterMetrics(
        long total,
        Map<String, Long> byReason,
        KafkaGatewayDeadLetterAlert alert
    ) {
    }

    record KafkaGatewayDeadLetterAlert(
        boolean active,
        int threshold,
        String reason
    ) {
    }
}
