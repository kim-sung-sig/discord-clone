package com.example.discord.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Per-user TOTP state; persistence adapters must provide cross-process atomicity. */
public final class TotpEnrollment {
    private final UUID owner;
    private final byte[] secret;
    private final Instant confirmationExpiresAt;
    private long consumedStep = -1;
    private boolean active;
    private int failures;

    public TotpEnrollment(UUID owner, byte[] secret, Instant confirmationExpiresAt) {
        this.owner = Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(secret, "secret");
        if (secret.length < 16) {
            throw new IllegalArgumentException("TOTP secret must contain at least 128 bits");
        }
        this.secret = secret.clone();
        this.confirmationExpiresAt = Objects.requireNonNull(confirmationExpiresAt, "confirmationExpiresAt");
    }

    public synchronized boolean confirm(UUID caller, String code, Instant now) {
        Objects.requireNonNull(now, "now");
        if (active || !now.isBefore(confirmationExpiresAt) || !owner.equals(caller)) {
            return false;
        }
        if (!consume(code, now)) {
            return false;
        }
        active = true;
        return true;
    }

    public synchronized boolean verify(UUID caller, String code, Instant now) {
        Objects.requireNonNull(now, "now");
        return active && owner.equals(caller) && consume(code, now);
    }

    private boolean consume(String code, Instant now) {
        // ponytail: fail-closed after five failures; account recovery/unlock belongs to the runtime service.
        if (failures >= 5) {
            return false;
        }
        var match = TotpVerifier.match(secret, code, now, consumedStep);
        if (match.isEmpty()) {
            failures++;
            return false;
        }
        consumedStep = match.getAsLong();
        failures = 0;
        return true;
    }
}
