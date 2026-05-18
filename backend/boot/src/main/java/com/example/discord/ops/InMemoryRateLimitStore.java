package com.example.discord.ops;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("!redis")
final class InMemoryRateLimitStore implements RateLimitStore {
    private final Map<WindowKey, CounterWindow> windows = new ConcurrentHashMap<>();

    @Override
    public RateLimitDecision consume(RateLimitKey key, RateLimitPolicy policy, Instant now) {
        long windowMillis = policy.window().toMillis();
        long windowStart = Math.floorDiv(now.toEpochMilli(), windowMillis) * windowMillis;
        WindowKey windowKey = new WindowKey(key.policyId(), key.subject(), windowStart);
        CounterWindow window = windows.compute(windowKey, (ignored, existing) -> {
            if (existing == null) {
                return new CounterWindow(1);
            }
            return new CounterWindow(existing.count() + 1);
        });

        int remaining = Math.max(policy.limit() - window.count(), 0);
        long retryAfterMillis = Math.max(windowStart + windowMillis - now.toEpochMilli(), 1);
        return new RateLimitDecision(
            window.count() <= policy.limit(),
            policy.limit(),
            remaining,
            Duration.ofMillis(retryAfterMillis)
        );
    }

    private record WindowKey(String policyId, String subject, long windowStartMillis) {
    }

    private record CounterWindow(int count) {
    }
}
