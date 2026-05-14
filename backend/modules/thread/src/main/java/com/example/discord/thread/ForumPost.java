package com.example.discord.thread;

import java.util.List;
import java.util.UUID;

public record ForumPost(ThreadChannel thread, List<UUID> tagIds) {
    public ForumPost {
        java.util.Objects.requireNonNull(thread, "thread must not be null");
        tagIds = List.copyOf(java.util.Objects.requireNonNull(tagIds, "tagIds must not be null"));
    }
}
