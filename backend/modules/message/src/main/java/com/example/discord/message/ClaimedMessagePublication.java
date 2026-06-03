package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record ClaimedMessagePublication(
    MessagePublished event,
    UUID claimToken
) {
    public ClaimedMessagePublication {
        Objects.requireNonNull(event, "event must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");
    }
}
