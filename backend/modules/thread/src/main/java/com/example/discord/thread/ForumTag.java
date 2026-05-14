package com.example.discord.thread;

import java.util.UUID;

public record ForumTag(UUID id, UUID guildId, UUID forumChannelId, String name) {
    public ForumTag {
        java.util.Objects.requireNonNull(id, "id must not be null");
        java.util.Objects.requireNonNull(guildId, "guildId must not be null");
        java.util.Objects.requireNonNull(forumChannelId, "forumChannelId must not be null");
        java.util.Objects.requireNonNull(name, "name must not be null");
    }
}
