package com.example.discord.gateway;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
final class RedisGatewaySessionRegistry implements GatewaySessionRegistry {
    private static final TypeReference<Map<String, Object>> SESSION_TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String registryKey;
    private final Duration sessionTtl;

    RedisGatewaySessionRegistry(
        StringRedisTemplate redis,
        ObjectMapper objectMapper,
        @Value("${discord.gateway.session-registry-key:gateway:sessions}") String registryKey,
        @Value("${discord.gateway.session-ttl-seconds:86400}") long sessionTtlSeconds
    ) {
        this.redis = Objects.requireNonNull(redis, "redis must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.registryKey = Objects.requireNonNull(registryKey, "registryKey must not be null");
        this.sessionTtl = Duration.ofSeconds(Math.max(60L, sessionTtlSeconds));
    }

    @Override
    public void save(GatewaySession session) {
        String sessionId = session.id().toString();
        redis.opsForValue().set(sessionKey(sessionId), encode(session), sessionTtl);
        redis.opsForSet().add(registryKey, sessionId);
        redis.expire(registryKey, sessionTtl);
    }

    @Override
    public Optional<GatewaySession> find(UUID sessionId) {
        String id = sessionId.toString();
        String encoded = redis.opsForValue().get(sessionKey(id));
        if (encoded == null) {
            prune(id);
            return Optional.empty();
        }
        Optional<GatewaySession> decoded = decode(encoded);
        if (decoded.isEmpty()) {
            prune(id);
        }
        return decoded;
    }

    @Override
    public Collection<GatewaySession> sessions() {
        Set<String> sessionIds = redis.opsForSet().members(registryKey);
        if (sessionIds == null || sessionIds.isEmpty()) {
            return java.util.List.of();
        }
        ArrayList<GatewaySession> sessions = new ArrayList<>();
        for (String sessionId : sessionIds) {
            String encoded = redis.opsForValue().get(sessionKey(sessionId));
            if (encoded == null) {
                prune(sessionId);
                continue;
            }
            Optional<GatewaySession> decoded = decode(encoded);
            if (decoded.isPresent()) {
                sessions.add(decoded.get());
            } else {
                prune(sessionId);
            }
        }
        return List.copyOf(sessions);
    }

    private String encode(GatewaySession session) {
        Map<String, Object> values = Map.of(
            "id", session.id().toString(),
            "userId", session.userId().toString(),
            "guildIds", session.guildIds().stream().map(UUID::toString).sorted().toList(),
            "lastAcknowledgedAt", session.lastAcknowledgedAt().toString(),
            "closed", session.closed(),
            "lastDeliveredSequence", session.lastDeliveredSequence()
        );
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("gateway session is not serializable", exception);
        }
    }

    private Optional<GatewaySession> decode(String encoded) {
        try {
            Map<String, Object> values = objectMapper.readValue(encoded, SESSION_TYPE);
            UUID sessionId = UUID.fromString(stringValue(values.get("id")));
            UUID userId = UUID.fromString(stringValue(values.get("userId")));
            @SuppressWarnings("unchecked")
            Set<UUID> guildIds = ((Collection<Object>) values.getOrDefault("guildIds", java.util.List.of())).stream()
                .map(Object::toString)
                .map(UUID::fromString)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Instant lastAcknowledgedAt = Instant.parse(stringValue(values.get("lastAcknowledgedAt")));
            boolean closed = Boolean.parseBoolean(stringValue(values.get("closed")));
            long lastDeliveredSequence = Long.parseLong(stringValue(values.get("lastDeliveredSequence")));
            return Optional.of(new GatewaySession(
                sessionId,
                userId,
                guildIds,
                lastAcknowledgedAt,
                closed,
                lastDeliveredSequence
            ));
        } catch (RuntimeException | JsonProcessingException exception) {
            return Optional.empty();
        }
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private String sessionKey(String sessionId) {
        return registryKey + ":" + sessionId;
    }

    private void prune(String sessionId) {
        redis.opsForSet().remove(registryKey, sessionId);
        redis.delete(sessionKey(sessionId));
    }
}
