package com.example.discord.identity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginFailureTrackerTest {
    @Test
    void locksEmailAfterRepeatedFailuresAndUnlocksAfterDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-13T00:00:00Z"));
        LoginFailureTracker tracker = new LoginFailureTracker(3, Duration.ofMinutes(15), clock);
        EmailAddress email = EmailAddress.from("user@example.com");

        tracker.recordFailure(email);
        tracker.recordFailure(email);
        assertThat(tracker.isLocked(email)).isFalse();

        tracker.recordFailure(email);
        assertThat(tracker.isLocked(email)).isTrue();
        assertThat(tracker.lockedUntil(email)).contains(Instant.parse("2026-05-13T00:15:00Z"));

        clock.moveTo(Instant.parse("2026-05-13T00:16:00Z"));
        assertThat(tracker.isLocked(email)).isFalse();
    }

    @Test
    void successfulLoginClearsFailures() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-13T00:00:00Z"));
        LoginFailureTracker tracker = new LoginFailureTracker(3, Duration.ofMinutes(15), clock);
        EmailAddress email = EmailAddress.from("user@example.com");

        tracker.recordFailure(email);
        tracker.clear(email);

        assertThat(tracker.failureCount(email)).isZero();
        assertThat(tracker.isLocked(email)).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void moveTo(Instant instant) {
            this.instant = instant;
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
            return instant;
        }
    }
}
