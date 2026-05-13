package com.example.discord.presence;

import java.time.Instant;
import java.util.UUID;

public record ReadMarker(UUID channelId, UUID userId, long lastReadSequence, Instant updatedAt) {
}
