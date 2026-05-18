package com.example.discord.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServerEvent(
    UUID id,
    UUID guildId,
    UUID channelId,
    UUID creatorId,
    String title,
    Instant startsAt,
    Instant endsAt,
    ServerEventStatus status,
    List<UUID> interestedMemberIds,
    Instant updatedAt
) {
    public ServerEvent {
        if (id == null || guildId == null || channelId == null || creatorId == null) {
            throw new IllegalArgumentException("event ids are required");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("event title is required");
        }
        if (startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("event time range is invalid");
        }
        if (status == null) {
            throw new IllegalArgumentException("event status is required");
        }
        interestedMemberIds = List.copyOf(interestedMemberIds == null ? List.of() : interestedMemberIds);
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }
}
