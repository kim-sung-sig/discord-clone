package com.example.discord.permission;

import java.util.Objects;

public final class AuthorizationProjectionConsumer {
    private final AuthorizationProjectionStore store;
    private final AuthorizationAudience audience;

    public AuthorizationProjectionConsumer(AuthorizationProjectionStore store, AuthorizationAudience audience) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.audience = Objects.requireNonNull(audience, "audience must not be null");
    }

    public ConsumeResult consume(AuthzProjectionUpdated event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.audience() != audience) {
            return new ConsumeResult(false, Outcome.IGNORED_AUDIENCE);
        }
        return new ConsumeResult(store.apply(event), Outcome.APPLIED_OR_DUPLICATE);
    }

    public ConsumeResult consume(AuthorizationWatermarkAdvanced event) {
        Objects.requireNonNull(event, "event must not be null");
        if (event.audience() != audience) {
            return new ConsumeResult(false, Outcome.IGNORED_AUDIENCE);
        }
        return new ConsumeResult(
            store.advanceWatermark(event),
            Outcome.APPLIED_OR_DUPLICATE
        );
    }

    public record ConsumeResult(boolean applied, Outcome outcome) {}

    public enum Outcome {
        APPLIED_OR_DUPLICATE,
        IGNORED_AUDIENCE
    }
}
