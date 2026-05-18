package com.example.discord.event;

import java.time.Instant;
import java.util.UUID;

public record CreateServerEventCommand(
    UUID guildId,
    UUID channelId,
    UUID creatorId,
    String title,
    Instant startsAt,
    Instant endsAt
) {
    public CreateServerEventCommand {
        if (guildId == null || channelId == null || creatorId == null) {
            throw new IllegalArgumentException("event ids are required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("event title is required");
        }
        if (startsAt == null || endsAt == null) {
            throw new IllegalArgumentException("event time range is required");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("event end must be after start");
        }
        title = title.trim();
    }
}
