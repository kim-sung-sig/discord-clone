package com.example.discord.ops;

import java.time.Duration;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@Profile("redis")
final class RedisRateLimitStore implements RateLimitStore {
    private final StringRedisTemplate redis;

    RedisRateLimitStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public RateLimitDecision consume(RateLimitKey key, RateLimitPolicy policy, Instant now) {
        long windowMillis = policy.window().toMillis();
        long windowStart = Math.floorDiv(now.toEpochMilli(), windowMillis) * windowMillis;
        String redisKey = "rl:%s:%s:%d".formatted(key.policyId(), key.subject(), windowStart);
        long retryAfterMillis = Math.max(windowStart + windowMillis - now.toEpochMilli(), 1);
        try {
            Long countValue = redis.opsForValue().increment(redisKey);
            long count = countValue == null ? policy.limit() + 1L : countValue;
            if (count == 1L) {
                redis.expire(redisKey, Duration.ofMillis(retryAfterMillis));
            }
            int remaining = Math.max(policy.limit() - Math.toIntExact(Math.min(count, Integer.MAX_VALUE)), 0);
            return new RateLimitDecision(
                count <= policy.limit(),
                policy.limit(),
                remaining,
                Duration.ofMillis(retryAfterMillis)
            );
        } catch (RedisConnectionFailureException exception) {
            return failClosed(policy);
        } catch (RuntimeException exception) {
            return failClosed(policy);
        }
    }

    private static RateLimitDecision failClosed(RateLimitPolicy policy) {
        return new RateLimitDecision(false, policy.limit(), 0, policy.window());
    }
}
