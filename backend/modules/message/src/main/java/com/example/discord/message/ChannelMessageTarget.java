package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record ChannelMessageTarget(
    UUID guildId,
    UUID channelId
) implements MessageTarget {
    public ChannelMessageTarget {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
    }
}
