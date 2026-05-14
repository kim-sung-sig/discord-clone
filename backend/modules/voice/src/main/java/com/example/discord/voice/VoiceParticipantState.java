package com.example.discord.voice;

import java.time.Instant;
import java.util.UUID;

public record VoiceParticipantState(
    UUID guildId,
    UUID channelId,
    UUID userId,
    boolean muted,
    boolean deafened,
    boolean speaking,
    boolean screenSharing,
    Instant updatedAt
) {
    public VoiceParticipantState {
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (updatedAt == null) {
            throw new IllegalArgumentException("updatedAt is required");
        }
    }

    VoiceParticipantState withState(
        boolean muted,
        boolean deafened,
        boolean speaking,
        boolean screenSharing,
        Instant updatedAt
    ) {
        return new VoiceParticipantState(guildId, channelId, userId, muted, deafened, speaking, screenSharing, updatedAt);
    }
}
