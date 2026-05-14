package com.example.discord.experience;

import java.time.Instant;

public record BillingCheckoutResult(
    boolean successful,
    String provider,
    String providerSubscriptionId,
    Instant expiresAt,
    String failureReason
) {
    public static BillingCheckoutResult success(String provider, String providerSubscriptionId, Instant expiresAt) {
        return new BillingCheckoutResult(true, provider, providerSubscriptionId, expiresAt, null);
    }

    public static BillingCheckoutResult failure(String reason) {
        return new BillingCheckoutResult(false, "local_test", null, null, reason);
    }
}
