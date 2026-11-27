package com.example.discord.message;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record MessageReadModel(
    UUID id,
    UUID guildId,
    UUID channelId,
    long sequence,
    UUID authorId,
    String content,
    List<String> mentions,
    boolean pinned,
    boolean deleted,
    boolean edited,
    Instant createdAt,
    Instant updatedAt
) {
    public MessageReadModel {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(authorId, "authorId must not be null");
        content = Objects.requireNonNull(content, "content must not be null");
        mentions = List.copyOf(Objects.requireNonNull(mentions, "mentions must not be null"));
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
    }

    public static MessageReadModel from(Message message, long sequence) {
        Objects.requireNonNull(message, "message must not be null");
        return new MessageReadModel(
            message.id(),
            message.guildId(),
            message.channelId(),
            sequence,
            message.authorId(),
            message.content().value(),
            MessageMentionTokens.tokens(message.mentions()),
            message.pinned(),
            message.deleted(),
            message.edited(),
            message.createdAt(),
            message.updatedAt()
        );
    }
}
