package com.example.discord.social;

import java.time.Instant;
import java.util.UUID;

public record DirectMessageReceipt(
    UUID id,
    UUID channelId,
    UUID senderId,
    UUID targetUserId,
    String content,
    Instant acceptedAt
) {
}
