package com.example.discord.message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Message(
    UUID id,
    UUID guildId,
    UUID channelId,
    UUID authorId,
    String content,
    List<String> mentions,
    boolean pinned,
    boolean deleted,
    List<MessageEdit> editHistory,
    Instant createdAt,
    Instant updatedAt
) {
    public Message {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(authorId, "authorId must not be null");
        content = Objects.requireNonNull(content, "content must not be null");
        mentions = List.copyOf(Objects.requireNonNull(mentions, "mentions must not be null"));
        editHistory = List.copyOf(Objects.requireNonNull(editHistory, "editHistory must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public boolean edited() {
        return !editHistory.isEmpty();
    }
}
