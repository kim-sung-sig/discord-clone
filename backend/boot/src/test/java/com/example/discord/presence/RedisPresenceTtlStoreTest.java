package com.example.discord.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisPresenceTtlStoreTest {
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisPresenceTtlStore store = new RedisPresenceTtlStore(redis);

    RedisPresenceTtlStoreTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void storesPresenceAndTypingValuesWithRedisTtl() {
        Instant observedAt = Instant.parse("2026-05-16T00:00:00Z");
        store.put(
            "presence:user:" + USER_ID,
            new UserPresence(USER_ID, PresenceStatus.ONLINE, observedAt),
            Duration.ofSeconds(30)
        );
        store.put(
            "typing:channel:" + CHANNEL_ID + ":" + USER_ID,
            new TypingIndicator(CHANNEL_ID, USER_ID, observedAt),
            Duration.ofSeconds(3)
        );

        verify(values).set(
            "presence:user:" + USER_ID,
            "presence|00000000-0000-0000-0000-000000000101|ONLINE|2026-05-16T00:00:00Z",
            Duration.ofSeconds(30)
        );
        verify(values).set(
            "typing:channel:" + CHANNEL_ID + ":" + USER_ID,
            "typing|00000000-0000-0000-0000-000000000202|00000000-0000-0000-0000-000000000101|2026-05-16T00:00:00Z",
            Duration.ofSeconds(3)
        );
    }

    @Test
    void readsPresenceAndTypingValuesFromRedisStrings() {
        when(values.get("presence:user:" + USER_ID))
            .thenReturn("presence|00000000-0000-0000-0000-000000000101|IDLE|2026-05-16T00:00:00Z");
        when(values.get("typing:channel:" + CHANNEL_ID + ":" + USER_ID))
            .thenReturn("typing|00000000-0000-0000-0000-000000000202|00000000-0000-0000-0000-000000000101|2026-05-16T00:00:01Z");

        Optional<Object> presence = store.get("presence:user:" + USER_ID);
        Optional<Object> typing = store.get("typing:channel:" + CHANNEL_ID + ":" + USER_ID);

        assertThat(presence).contains(new UserPresence(USER_ID, PresenceStatus.IDLE, Instant.parse("2026-05-16T00:00:00Z")));
        assertThat(typing).contains(new TypingIndicator(CHANNEL_ID, USER_ID, Instant.parse("2026-05-16T00:00:01Z")));
    }

    @Test
    void degradesToAbsentWhenRedisIsUnavailable() {
        when(values.get("presence:user:" + USER_ID)).thenThrow(new RedisConnectionFailureException("redis unavailable"));
        doThrow(new RedisConnectionFailureException("redis unavailable"))
            .when(values)
            .set(anyString(), anyString(), any(Duration.class));
        when(redis.keys("typing:channel:" + CHANNEL_ID + ":*")).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        assertThat(store.get("presence:user:" + USER_ID)).isEmpty();
        assertThat(store.keys("typing:channel:" + CHANNEL_ID + ":")).isEmpty();
        store.put("presence:user:" + USER_ID, new UserPresence(USER_ID, PresenceStatus.ONLINE, Instant.now()), Duration.ofSeconds(30));
    }

    @Test
    void listsKeysByPrefix() {
        when(redis.keys("typing:channel:" + CHANNEL_ID + ":*"))
            .thenReturn(Set.of("typing:channel:" + CHANNEL_ID + ":" + USER_ID));

        assertThat(store.keys("typing:channel:" + CHANNEL_ID + ":"))
            .containsExactly("typing:channel:" + CHANNEL_ID + ":" + USER_ID);
    }
}
