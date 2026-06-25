package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record ThreadMessageTarget(
    UUID guildId,
    UUID channelId,
    UUID threadId
) implements MessageTarget {
    public ThreadMessageTarget {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(threadId, "threadId must not be null");
    }
}
