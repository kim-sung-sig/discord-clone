package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record ChannelMentionTarget(UUID channelId) implements MessageMentionTarget {
    public ChannelMentionTarget {
        Objects.requireNonNull(channelId, "channelId must not be null");
    }
}
