package com.example.discord.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
final class RedisGatewayEventBus implements GatewayEventBus {
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String nodeId;
    private final Set<String> subscribedStreams = ConcurrentHashMap.newKeySet();
    private final Map<String, String> streamOffsets = new ConcurrentHashMap<>();
    private final List<Consumer<GatewayBusEvent>> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

    RedisGatewayEventBus(
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        Clock clock,
        @Value("${discord.gateway.node-id:${random.uuid}}") String nodeId
    ) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.nodeId = Objects.requireNonNull(nodeId, "nodeId must not be null");
        subscribedStreams.add("gateway:global");
        streamOffsets.put("gateway:global", "0-0");
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
        notifyListeners(event);
        return event;
    }

    @Override
    public void addEventListener(Consumer<GatewayBusEvent> listener) {
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
        String offset = streamOffsets.getOrDefault(stream, "0-0");
        @SuppressWarnings({"rawtypes", "unchecked"})
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
            StreamReadOptions.empty().count(100),
            StreamOffset.create(stream, ReadOffset.from(offset))
        );
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            streamOffsets.put(stream, record.getId().getValue());
            decode(record).ifPresent(this::notifyListeners);
        }
    }

    private void subscribe(String stream) {
        subscribedStreams.add(stream);
        streamOffsets.putIfAbsent(stream, "0-0");
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
        for (Consumer<GatewayBusEvent> listener : listeners) {
            listener.accept(event);
        }
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
}
