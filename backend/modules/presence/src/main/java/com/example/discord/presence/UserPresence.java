package com.example.discord.presence;

import java.time.Instant;
import java.util.UUID;

public record UserPresence(UUID userId, PresenceStatus status, Instant updatedAt) {
}
