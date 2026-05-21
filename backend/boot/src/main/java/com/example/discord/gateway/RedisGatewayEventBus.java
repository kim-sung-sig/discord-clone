package com.example.discord.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("redis & !kafka")
final class RedisGatewayEventBus implements GatewayEventBus {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String nodeId;
    private final String consumerGroup;
    private final long maxStreamLength;
    private final String deadLetterStream;
    private final int deadLetterAlertThreshold;
    private final Set<String> subscribedStreams = ConcurrentHashMap.newKeySet();
    private final Set<String> initializedConsumerGroups = ConcurrentHashMap.newKeySet();
    private final List<java.util.function.Consumer<GatewayBusEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final AtomicLong processedTotal = new AtomicLong();
    private final AtomicLong ackedTotal = new AtomicLong();
    private final AtomicLong failedDecodeTotal = new AtomicLong();
    private final AtomicLong readFailureTotal = new AtomicLong();
    private final AtomicLong trimmedTotal = new AtomicLong();
    private final AtomicLong deadLetterTotal = new AtomicLong();
    private final ConcurrentMap<String, AtomicLong> deadLettersByReason = new ConcurrentHashMap<>();

    RedisGatewayEventBus(
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${discord.gateway.node-id:${random.uuid}}") String nodeId
    ) {
        this(redis, objectMapper, clock, nodeId, "discord-gateway", 10_000L);
    }

    RedisGatewayEventBus(
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${discord.gateway.node-id:${random.uuid}}") String nodeId,
        @Value("${discord.gateway.redis-stream-consumer-group:discord-gateway}") String consumerGroupPrefix,
        @Value("${discord.gateway.redis-stream-max-length:10000}") long maxStreamLength
    ) {
        this(redis, objectMapper, clock, nodeId, consumerGroupPrefix, maxStreamLength, "gateway:dead-letter", 1);
    }

    RedisGatewayEventBus(
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${discord.gateway.node-id:${random.uuid}}") String nodeId,
        @Value("${discord.gateway.redis-stream-consumer-group:discord-gateway}") String consumerGroupPrefix,
        @Value("${discord.gateway.redis-stream-max-length:10000}") long maxStreamLength,
        @Value("${discord.gateway.redis-dlq-stream:gateway:dead-letter}") String deadLetterStream,
        @Value("${discord.gateway.redis-dlq-alert-threshold:1}") int deadLetterAlertThreshold
    ) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        this.consumerGroup = nodeScopedConsumerGroup(consumerGroupPrefix, nodeId);
        this.maxStreamLength = Math.max(1L, maxStreamLength);
        this.deadLetterStream = defaultIfBlank(deadLetterStream, "gateway:dead-letter");
        this.deadLetterAlertThreshold = Math.max(0, deadLetterAlertThreshold);
        subscribedStreams.add("gateway:global");
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
        String streamKey = streamKey(event.guildId(), event.channelId());
        subscribedStreams.add(streamKey);
        redis.opsForStream().add(MapRecord.create(streamKey, encode(event)));
        trim(streamKey);
        notifyListeners(event);
        return event;
    }

    @Override
    public void addEventListener(java.util.function.Consumer<GatewayBusEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    @Override
    public void subscribeGuild(UUID guildId) {
        if (guildId != null) {
            subscribe("gateway:guild:" + guildId);
        }
    }

    @Override
    public void subscribeChannel(UUID channelId) {
        if (channelId != null) {
            subscribe("gateway:channel:" + channelId);
        }
    }

    @Scheduled(fixedDelayString = "${discord.gateway.redis-stream-poll-delay-ms:250}")
    void pollOnce() {
        for (String stream : Set.copyOf(subscribedStreams)) {
            readStream(stream);
        }
    }

    private void readStream(String stream) {
        ensureConsumerGroup(stream);
        readStream(stream, ReadOffset.from("0-0"));
        readStream(stream, ReadOffset.lastConsumed());
    }

    private void readStream(String stream, ReadOffset offset) {
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MapRecord<String, Object, Object>> records;
        try {
            records = redis.opsForStream().read(
                Consumer.from(consumerGroup, nodeId),
                StreamReadOptions.empty().count(100),
                StreamOffset.create(stream, offset)
            );
        } catch (RuntimeException exception) {
            readFailureTotal.incrementAndGet();
            return;
        }
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            java.util.Optional<GatewayBusEvent> decoded = decode(record);
            if (decoded.isPresent()) {
                if (!isOwnSourceRecord(record)) {
                    processedTotal.incrementAndGet();
                    notifyRemoteListeners(decoded.get(), record);
                }
            } else {
                failedDecodeTotal.incrementAndGet();
                publishDeadLetter("MALFORMED_RECORD", record, null);
            }
            redis.opsForStream().acknowledge(record.getStream(), consumerGroup, record.getId());
            ackedTotal.incrementAndGet();
        }
    }

    private boolean isOwnSourceRecord(MapRecord<String, Object, Object> record) {
        return nodeId.equals(stringValue(record.getValue().get("sourceNodeId")));
    }

    private void subscribe(String stream) {
        subscribedStreams.add(stream);
    }

    private void ensureConsumerGroup(String stream) {
        if (!initializedConsumerGroups.add(stream)) {
            return;
        }
        try {
            redis.opsForStream().createGroup(stream, ReadOffset.from("0-0"), consumerGroup);
        } catch (RuntimeException exception) {
            // Redis returns BUSYGROUP if another node created it, and may reject empty streams.
        }
    }

    private void trim(String stream) {
        Long trimmed = redis.opsForStream().trim(stream, maxStreamLength);
        trimmedTotal.addAndGet(trimmed == null ? 0L : trimmed);
    }

    private Map<String, String> encode(GatewayBusEvent event) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("eventId", event.eventId());
        values.put("type", event.type());
        values.put("guildId", event.guildId().toString());
        values.put("channelId", event.channelId() == null ? "" : event.channelId().toString());
        values.put("payload", writePayload(event.payload()));
        values.put("createdAt", event.createdAt().toString());
        values.put("sourceNodeId", nodeId);
        return values;
    }

    private java.util.Optional<GatewayBusEvent> decode(MapRecord<String, Object, Object> record) {
        try {
            Map<Object, Object> values = record.getValue();
            String eventId = stringValue(values.get("eventId"));
            String type = stringValue(values.get("type"));
            UUID guildId = UUID.fromString(stringValue(values.get("guildId")));
            String channelValue = stringValue(values.get("channelId"));
            UUID channelId = channelValue.isBlank() ? null : UUID.fromString(channelValue);
            Map<String, Object> payload = objectMapper.readValue(stringValue(values.get("payload")), PAYLOAD_TYPE);
            Instant createdAt = Instant.parse(stringValue(values.get("createdAt")));
            return java.util.Optional.of(new GatewayBusEvent(eventId, type, guildId, channelId, payload, createdAt));
        } catch (RuntimeException | JsonProcessingException exception) {
            return java.util.Optional.empty();
        }
    }

    private void notifyListeners(GatewayBusEvent event) {
        for (java.util.function.Consumer<GatewayBusEvent> listener : listeners) {
            listener.accept(event);
        }
    }

    private void notifyRemoteListeners(GatewayBusEvent event, MapRecord<String, Object, Object> record) {
        for (java.util.function.Consumer<GatewayBusEvent> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                publishDeadLetter("LISTENER_FAILURE", record, event);
            }
        }
    }

    RedisGatewayStreamMetrics metrics() {
        return new RedisGatewayStreamMetrics(
            processedTotal.get(),
            ackedTotal.get(),
            failedDecodeTotal.get(),
            readFailureTotal.get(),
            trimmedTotal.get()
        );
    }

    RedisGatewayDeadLetterMetrics deadLetterMetrics() {
        long total = deadLetterTotal.get();
        Map<String, Long> byReason = deadLettersByReason.entrySet().stream()
            .collect(java.util.stream.Collectors.toUnmodifiableMap(
                Map.Entry::getKey,
                entry -> entry.getValue().get()
            ));
        return new RedisGatewayDeadLetterMetrics(total, byReason, deadLetterAlert(total));
    }

    private void publishDeadLetter(String reason, MapRecord<String, Object, Object> record, GatewayBusEvent event) {
        recordDeadLetterMetrics(reason);
        Map<Object, Object> values = record.getValue();
        String message = values.toString();
        Map<String, String> deadLetter = new LinkedHashMap<>();
        deadLetter.put("reason", reason);
        deadLetter.put("handlingNodeId", nodeId);
        deadLetter.put("sourceNodeId", stringValue(values.get("sourceNodeId")));
        deadLetter.put("eventId", event == null ? stringValue(values.get("eventId")) : event.eventId());
        deadLetter.put("type", event == null ? stringValue(values.get("type")) : event.type());
        deadLetter.put("receivedAt", clock.instant().toString());
        deadLetter.put("stream", stringValue(record.getStream()));
        deadLetter.put("recordId", record.getId().getValue());
        deadLetter.put("messageSize", Integer.toString(message.length()));
        deadLetter.put("messageSha256Prefix", sha256Prefix(message));
        redis.opsForStream().add(MapRecord.create(deadLetterStream, deadLetter));
        trim(deadLetterStream);
    }

    private void recordDeadLetterMetrics(String reason) {
        deadLetterTotal.incrementAndGet();
        deadLettersByReason.computeIfAbsent(reason, ignored -> new AtomicLong()).incrementAndGet();
    }

    private RedisGatewayDeadLetterAlert deadLetterAlert(long total) {
        boolean active = deadLetterAlertThreshold > 0 && total >= deadLetterAlertThreshold;
        String reason = active
            ? "DLQ count %d reached threshold %d".formatted(total, deadLetterAlertThreshold)
            : "";
        return new RedisGatewayDeadLetterAlert(active, deadLetterAlertThreshold, reason);
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("gateway payload is not serializable", exception);
        }
    }

    private static String streamKey(UUID guildId, UUID channelId) {
        if (channelId != null) {
            return "gateway:channel:" + channelId;
        }
        if (guildId != null) {
            return "gateway:guild:" + guildId;
        }
        return "gateway:global";
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String defaultIfBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String nodeScopedConsumerGroup(String consumerGroupPrefix, String nodeId) {
        String prefix = Objects.requireNonNull(consumerGroupPrefix, "consumerGroupPrefix must not be null").trim();
        String node = Objects.requireNonNull(nodeId, "nodeId must not be null").trim();
        if (prefix.isBlank()) {
            prefix = "discord-gateway";
        }
        if (node.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        return prefix + ":" + node;
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

    record RedisGatewayStreamMetrics(
        long processedTotal,
        long ackedTotal,
        long failedDecodeTotal,
        long readFailureTotal,
        long trimmedTotal
    ) {
    }

    record RedisGatewayDeadLetterMetrics(
        long total,
        Map<String, Long> byReason,
        RedisGatewayDeadLetterAlert alert
    ) {
    }

    record RedisGatewayDeadLetterAlert(
        boolean active,
        int threshold,
        String reason
    ) {
    }
}
