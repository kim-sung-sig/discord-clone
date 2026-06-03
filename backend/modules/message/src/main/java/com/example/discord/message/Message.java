package com.example.discord.message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record Message(
    UUID id,
    MessageAuthor author,
    MessageTarget target,
    MessageContent content,
    List<MessageMentionTarget> mentions,
    boolean pinned,
    boolean deleted,
    List<MessageEdit> editHistory,
    Instant createdAt,
    Instant updatedAt
) {
    public Message {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(content, "content must not be null");
        mentions = List.copyOf(Objects.requireNonNull(mentions, "mentions must not be null"));
        editHistory = List.copyOf(Objects.requireNonNull(editHistory, "editHistory must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public boolean edited() {
        return !editHistory.isEmpty();
    }

    public UUID guildId() {
        if (target instanceof ChannelMessageTarget channel) {
            return channel.guildId();
        }
        if (target instanceof ThreadMessageTarget thread) {
            return thread.guildId();
        }
        throw new UnsupportedOperationException("message target does not have guildId");
    }

    public UUID channelId() {
        if (target instanceof ChannelMessageTarget channel) {
            return channel.channelId();
        }
        if (target instanceof ThreadMessageTarget thread) {
            return thread.channelId();
        }
        throw new UnsupportedOperationException("message target does not have channelId");
    }

    public UUID authorId() {
        if (author instanceof UserMessageAuthor user) {
            return user.userId();
        }
        throw new UnsupportedOperationException("message author does not have user authorId");
    }
}
