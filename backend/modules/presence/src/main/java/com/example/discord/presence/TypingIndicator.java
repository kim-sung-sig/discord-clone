package com.example.discord.presence;

import java.time.Instant;
import java.util.UUID;

public record TypingIndicator(UUID channelId, UUID userId, Instant updatedAt) {
}
