package com.example.discord.presence;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public interface PresenceTtlStore {
    void put(String key, Object value, Duration ttl);

    Optional<Object> get(String key);

    Set<String> keys(String prefix);

    void remove(String key);
}
