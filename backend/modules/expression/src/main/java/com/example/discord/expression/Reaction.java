package com.example.discord.expression;

import java.util.UUID;

public record Reaction(
    UUID channelId,
    UUID messageId,
    String emojiKey,
    UUID userId
) {
}
