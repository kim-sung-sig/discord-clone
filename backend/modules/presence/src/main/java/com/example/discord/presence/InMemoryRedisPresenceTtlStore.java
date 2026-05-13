package com.example.discord.presence;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class InMemoryRedisPresenceTtlStore implements PresenceTtlStore {
    private final Clock clock;
    private final Map<String, Entry> entries = new LinkedHashMap<>();

    public InMemoryRedisPresenceTtlStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public synchronized void put(String key, Object value, Duration ttl) {
        requireKey(key);
        Objects.requireNonNull(value, "value must not be null");
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            entries.remove(key);
            return;
        }
        entries.put(key, new Entry(value, clock.instant().plus(ttl)));
    }

    @Override
    public synchronized Optional<Object> get(String key) {
        requireKey(key);
        purgeExpired();
        Entry entry = entries.get(key);
        return entry == null ? Optional.empty() : Optional.of(entry.value());
    }

    @Override
    public synchronized Set<String> keys(String prefix) {
        Objects.requireNonNull(prefix, "prefix must not be null");
        purgeExpired();
        Set<String> matching = new TreeSet<>();
        for (String key : entries.keySet()) {
            if (key.startsWith(prefix)) {
                matching.add(key);
            }
        }
        return matching;
    }

    @Override
    public synchronized void remove(String key) {
        requireKey(key);
        entries.remove(key);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        entries.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
    }

    private static void requireKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("key is required");
        }
    }

    private record Entry(Object value, Instant expiresAt) {
    }
}
