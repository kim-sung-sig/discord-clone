package com.example.discord.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class LoginFailureTracker {
    private final int threshold;
    private final Duration lockDuration;
    private final Clock clock;
    private final ConcurrentMap<String, FailureState> failures = new ConcurrentHashMap<>();

    public LoginFailureTracker(int threshold, Duration lockDuration, Clock clock) {
        if (threshold < 1) {
            throw new IllegalArgumentException("threshold must be positive");
        }
        this.threshold = threshold;
        this.lockDuration = lockDuration;
        this.clock = clock;
    }

    public void recordFailure(EmailAddress email) {
        failures.compute(email.value(), (key, current) -> {
            FailureState state = current == null ? FailureState.empty() : current;
            int nextCount = state.count() + 1;
            Instant lockedUntil = nextCount >= threshold ? clock.instant().plus(lockDuration) : state.lockedUntil();
            return new FailureState(nextCount, lockedUntil);
        });
    }

    public void clear(EmailAddress email) {
        failures.remove(email.value());
    }

    public boolean isLocked(EmailAddress email) {
        return lockedUntil(email).filter(until -> clock.instant().isBefore(until)).isPresent();
    }

    public Optional<Instant> lockedUntil(EmailAddress email) {
        FailureState state = failures.get(email.value());
        if (state == null || state.lockedUntil() == null || !clock.instant().isBefore(state.lockedUntil())) {
            return Optional.empty();
        }
        return Optional.of(state.lockedUntil());
    }

    public int failureCount(EmailAddress email) {
        FailureState state = failures.get(email.value());
        return state == null ? 0 : state.count();
    }

    private record FailureState(int count, Instant lockedUntil) {
        private static FailureState empty() {
            return new FailureState(0, null);
        }
    }
}
