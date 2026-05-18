package com.example.discord.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisRateLimitStoreTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisRateLimitStore store = new RedisRateLimitStore(redis);

    RedisRateLimitStoreTest() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void consumesFixedWindowCountersInRedis() {
        RateLimitPolicy policy = new RateLimitPolicy("auth-login", 2, Duration.ofMinutes(1));
        RateLimitKey key = new RateLimitKey("auth-login", "ip:subject-hash");
        Instant now = Instant.parse("2026-05-16T00:00:01Z");
        when(values.increment(anyString())).thenReturn(1L, 2L, 3L);

        RateLimitDecision first = store.consume(key, policy, now);
        RateLimitDecision second = store.consume(key, policy, now.plusSeconds(1));
        RateLimitDecision third = store.consume(key, policy, now.plusSeconds(2));

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(1);
        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isZero();
        assertThat(third.allowed()).isFalse();
        assertThat(third.retryAfter()).isEqualTo(Duration.ofSeconds(57));
        verify(redis).expire(eq("rl:auth-login:ip:subject-hash:1778889600000"), eq(Duration.ofSeconds(59)));
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        RateLimitPolicy policy = new RateLimitPolicy("message-create", 30, Duration.ofMinutes(1));
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("redis unavailable"));

        RateLimitDecision decision = store.consume(
            new RateLimitKey("message-create", "token:subject-hash"),
            policy,
            Instant.parse("2026-05-16T00:00:00Z")
        );

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.limit()).isEqualTo(30);
        assertThat(decision.remaining()).isZero();
        assertThat(decision.retryAfter()).isEqualTo(Duration.ofMinutes(1));
    }
}
