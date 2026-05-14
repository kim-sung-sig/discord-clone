package com.example.discord.voice;

import java.time.Instant;
import java.util.UUID;

public record VoiceStateEvent(
    String type,
    UUID guildId,
    UUID channelId,
    UUID userId,
    boolean muted,
    boolean deafened,
    boolean speaking,
    boolean screenSharing,
    Instant occurredAt
) {
    public static final String JOINED = "VOICE_STATE_JOINED";
    public static final String LEFT = "VOICE_STATE_LEFT";
    public static final String UPDATED = "VOICE_STATE_UPDATE";

    public VoiceStateEvent {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type is required");
        }
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
        if (occurredAt == null) {
            throw new IllegalArgumentException("occurredAt is required");
        }
    }

    static VoiceStateEvent from(String type, VoiceParticipantState state, Instant occurredAt) {
        return new VoiceStateEvent(
            type,
            state.guildId(),
            state.channelId(),
            state.userId(),
            state.muted(),
            state.deafened(),
            state.speaking(),
            state.screenSharing(),
            occurredAt
        );
    }
}
