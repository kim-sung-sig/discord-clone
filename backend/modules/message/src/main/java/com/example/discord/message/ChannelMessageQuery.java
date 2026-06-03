package com.example.discord.message;

import java.util.Objects;

public record ChannelMessageQuery(
    MessageAuthor requester,
    ChannelMessageTarget target,
    String beforeCursor,
    int limit
) {
    public ChannelMessageQuery {
        Objects.requireNonNull(requester, "requester must not be null");
        Objects.requireNonNull(target, "target must not be null");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }
}
