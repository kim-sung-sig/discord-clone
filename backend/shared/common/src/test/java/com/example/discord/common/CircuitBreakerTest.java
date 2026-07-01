package com.example.discord.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class CircuitBreakerTest {
    @Test
    void limitsHalfOpenProbesBeforeClosing() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-30T00:00:00Z"));
        CircuitBreaker breaker = new CircuitBreaker(
            "gateway-publish",
            2,
            Duration.ofSeconds(5),
            2,
            2,
            clock
        );

        breaker.tryAcquire().orElseThrow().recordFailure();
        breaker.tryAcquire().orElseThrow().recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isEmpty();

        clock.advance(Duration.ofSeconds(5));

        CircuitBreaker.Permit firstProbe = breaker.tryAcquire().orElseThrow();
        CircuitBreaker.Permit secondProbe = breaker.tryAcquire().orElseThrow();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.tryAcquire()).isEmpty();

        firstProbe.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        secondProbe.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.tryAcquire()).isPresent();
    }

    @Test
    void reopensWhenHalfOpenProbeFails() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-30T00:00:00Z"));
        CircuitBreaker breaker = new CircuitBreaker(
            "outbox-relay",
            1,
            Duration.ofSeconds(5),
            1,
            1,
            clock
        );

        breaker.tryAcquire().orElseThrow().recordFailure();
        clock.advance(Duration.ofSeconds(5));

        breaker.tryAcquire().orElseThrow().recordFailure();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.OPEN);
        assertThat(breaker.tryAcquire()).isEmpty();
    }

    @Test
    void ignoresStaleHalfOpenPermitAfterNewGenerationStarts() {
        MutableClock clock = new MutableClock(Instant.parse("2026-06-30T00:00:00Z"));
        CircuitBreaker breaker = new CircuitBreaker(
            "external-plugin",
            1,
            Duration.ofSeconds(5),
            2,
            1,
            clock
        );

        breaker.tryAcquire().orElseThrow().recordFailure();
        clock.advance(Duration.ofSeconds(5));

        CircuitBreaker.Permit staleProbe = breaker.tryAcquire().orElseThrow();
        breaker.tryAcquire().orElseThrow().recordFailure();

        clock.advance(Duration.ofSeconds(5));
        CircuitBreaker.Permit currentProbe = breaker.tryAcquire().orElseThrow();

        staleProbe.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.HALF_OPEN);
        assertThat(breaker.tryAcquire()).isPresent();

        currentProbe.recordSuccess();

        assertThat(breaker.state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
