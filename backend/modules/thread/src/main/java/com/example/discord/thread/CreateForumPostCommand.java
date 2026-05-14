package com.example.discord.thread;

import java.util.List;
import java.util.UUID;

public record CreateForumPostCommand(
    UUID guildId,
    UUID forumChannelId,
    UUID authorId,
    String title,
    List<UUID> tagIds,
    int autoArchiveMinutes
) {
    public CreateForumPostCommand {
        tagIds = List.copyOf(java.util.Objects.requireNonNull(tagIds, "tagIds must not be null"));
    }
}
