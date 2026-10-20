package com.example.discord.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class TotpEnrollmentTest {
    @Test
    void requiresConfirmationAndBindsOwner() {
        UUID owner = UUID.randomUUID();
        TotpEnrollment enrollment = enrollment(owner);
        assertFalse(enrollment.verify(owner, "287082", Instant.ofEpochSecond(59)));
        assertFalse(enrollment.confirm(UUID.randomUUID(), "287082", Instant.ofEpochSecond(59)));
        assertTrue(enrollment.confirm(owner, "287082", Instant.ofEpochSecond(59)));
        assertFalse(enrollment.verify(owner, "287082", Instant.ofEpochSecond(59)));
        assertFalse(enrollment.confirm(owner, "287082", Instant.ofEpochSecond(59)));
    }

    @Test
    void consumesConfirmationOnlyOnceUnderConcurrency() throws Exception {
        UUID owner = UUID.randomUUID();
        TotpEnrollment enrollment = enrollment(owner);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> enrollment.confirm(owner, "287082", Instant.ofEpochSecond(59)));
            var second = executor.submit(() -> enrollment.confirm(owner, "287082", Instant.ofEpochSecond(59)));
            assertNotEquals(first.get(), second.get());
        }
    }

    @Test
    void pendingEnrollmentExpiresAndLocksAfterFiveFailures() {
        UUID owner = UUID.randomUUID();
        assertFalse(enrollment(owner).confirm(owner, "287082", Instant.ofEpochSecond(300)));
        TotpEnrollment enrollment = enrollment(owner);
        for (int i = 0; i < 5; i++) {
            assertFalse(enrollment.confirm(owner, "invalid", Instant.ofEpochSecond(59)));
        }
        assertFalse(enrollment.confirm(owner, "287082", Instant.ofEpochSecond(59)));
    }

    private TotpEnrollment enrollment(UUID owner) {
        return new TotpEnrollment(owner, "12345678901234567890".getBytes(StandardCharsets.US_ASCII),
            Instant.ofEpochSecond(300));
    }
}
