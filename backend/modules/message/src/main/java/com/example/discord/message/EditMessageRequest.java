package com.example.discord.message;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EditMessageRequest(
    UUID messageId,
    MessageAuthor editor,
    MessageContent content,
    List<MessageMentionTarget> mentions
) {
    public EditMessageRequest {
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(editor, "editor must not be null");
        Objects.requireNonNull(content, "content must not be null");
        mentions = List.copyOf(Objects.requireNonNull(mentions, "mentions must not be null"));
    }
}
