package com.example.discord.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
final class RedisPresenceTtlStore implements PresenceTtlStore {
    private final StringRedisTemplate redis;

    RedisPresenceTtlStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String key, Object value, Duration ttl) {
        requireKey(key);
        Objects.requireNonNull(value, "value must not be null");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            remove(key);
            return;
        }
        try {
            redis.opsForValue().set(key, encode(value), ttl);
        } catch (RuntimeException exception) {
            // Presence is ephemeral. Redis outages degrade to offline/empty instead of failing unrelated requests.
        }
    }

    @Override
    public Optional<Object> get(String key) {
        requireKey(key);
        try {
            return Optional.ofNullable(redis.opsForValue().get(key)).flatMap(RedisPresenceTtlStore::decode);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    @Override
    public Set<String> keys(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        try {
            Set<String> keys = redis.keys(prefix + "*");
            return keys == null ? Set.of() : new TreeSet<>(keys);
        } catch (RuntimeException exception) {
            return Set.of();
        }
    }

    @Override
    public void remove(String key) {
        requireKey(key);
        try {
            redis.delete(key);
        } catch (RuntimeException exception) {
            // Presence is ephemeral. Remove failures are treated as degraded offline behavior.
        }
    }

    private static String encode(Object value) {
        if (value instanceof UserPresence presence) {
            return "presence|%s|%s|%s".formatted(
                presence.userId(),
                presence.status().name(),
                presence.updatedAt()
            );
        }
        if (value instanceof TypingIndicator typing) {
            return "typing|%s|%s|%s".formatted(
                typing.channelId(),
                typing.userId(),
                typing.updatedAt()
            );
        }
        throw new IllegalArgumentException("unsupported presence TTL value type: " + value.getClass().getName());
    }

    private static Optional<Object> decode(String value) {
        String[] parts = value.split("\\|", -1);
        if (parts.length == 4 && "presence".equals(parts[0])) {
            return decodePresence(parts);
        }
        if (parts.length == 4 && "typing".equals(parts[0])) {
            return decodeTyping(parts);
        }
        return Optional.empty();
    }

    private static Optional<Object> decodePresence(String[] parts) {
        try {
            return Optional.of(new UserPresence(
                UUID.fromString(parts[1]),
                PresenceStatus.valueOf(parts[2]),
                Instant.parse(parts[3])
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Object> decodeTyping(String[] parts) {
        try {
            return Optional.of(new TypingIndicator(
                UUID.fromString(parts[1]),
                UUID.fromString(parts[2]),
                Instant.parse(parts[3])
            ));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
    }
}
