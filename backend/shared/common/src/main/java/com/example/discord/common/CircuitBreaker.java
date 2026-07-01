package com.example.discord.common;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class CircuitBreaker {
    public enum State {
        CLOSED,
        OPEN,
        HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;
    private final int halfOpenMaxConcurrentProbes;
    private final int halfOpenSuccessThreshold;
    private final Clock clock;
    private State state = State.CLOSED;
    private int closedFailures;
    private int halfOpenInFlight;
    private int halfOpenSuccesses;
    private long generation;
    private Instant openedAt;

    public CircuitBreaker(
        String name,
        int failureThreshold,
        Duration openDuration,
        int halfOpenMaxConcurrentProbes,
        int halfOpenSuccessThreshold,
        Clock clock
    ) {
        this.name = requireText(name, "name");
        this.failureThreshold = requirePositive(failureThreshold, "failureThreshold");
        this.openDuration = requirePositive(openDuration, "openDuration");
        this.halfOpenMaxConcurrentProbes = requirePositive(
            halfOpenMaxConcurrentProbes,
            "halfOpenMaxConcurrentProbes"
        );
        this.halfOpenSuccessThreshold = requirePositive(
            halfOpenSuccessThreshold,
            "halfOpenSuccessThreshold"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized State state() {
        return state;
    }

    public synchronized Optional<Permit> tryAcquire() {
        transitionToHalfOpenIfReady();

        if (state == State.OPEN) {
            return Optional.empty();
        }

        if (state == State.HALF_OPEN) {
            if (halfOpenInFlight >= halfOpenMaxConcurrentProbes) {
                return Optional.empty();
            }
            halfOpenInFlight++;
        }

        return Optional.of(new Permit(this, generation));
    }

    public String name() {
        return name;
    }

    private synchronized void recordSuccess(long permitGeneration) {
        if (permitGeneration != generation) {
            return;
        }

        if (state == State.CLOSED) {
            closedFailures = 0;
            return;
        }

        if (state == State.HALF_OPEN) {
            halfOpenInFlight--;
            halfOpenSuccesses++;
            if (halfOpenSuccesses >= halfOpenSuccessThreshold) {
                close();
            }
        }
    }

    private synchronized void recordFailure(long permitGeneration) {
        if (permitGeneration != generation) {
            return;
        }

        if (state == State.HALF_OPEN) {
            halfOpenInFlight--;
            open();
            return;
        }

        if (state == State.CLOSED) {
            closedFailures++;
            if (closedFailures >= failureThreshold) {
                open();
            }
        }
    }

    private void transitionToHalfOpenIfReady() {
        if (state == State.OPEN && !clock.instant().isBefore(openedAt.plus(openDuration))) {
            state = State.HALF_OPEN;
            generation++;
            halfOpenInFlight = 0;
            halfOpenSuccesses = 0;
        }
    }

    private void open() {
        state = State.OPEN;
        generation++;
        closedFailures = 0;
        halfOpenInFlight = 0;
        halfOpenSuccesses = 0;
        openedAt = clock.instant();
    }

    private void close() {
        state = State.CLOSED;
        generation++;
        closedFailures = 0;
        halfOpenInFlight = 0;
        halfOpenSuccesses = 0;
        openedAt = null;
    }

    private static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    public static final class Permit {
        private final CircuitBreaker breaker;
        private final long generation;
        private boolean completed;

        private Permit(CircuitBreaker breaker, long generation) {
            this.breaker = breaker;
            this.generation = generation;
        }

        public void recordSuccess() {
            complete(true);
        }

        public void recordFailure() {
            complete(false);
        }

        private synchronized void complete(boolean success) {
            if (completed) {
                return;
            }
            completed = true;
            if (success) {
                breaker.recordSuccess(generation);
            } else {
                breaker.recordFailure(generation);
            }
        }
    }
}
