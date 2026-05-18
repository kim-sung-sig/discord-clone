package com.example.discord.voice;

import java.time.Instant;

public record VoiceRoomToken(
    String room,
    String participant,
    String token,
    String provider,
    Instant expiresAt
) {
    public static final String PROVIDER = "LIVEKIT_SKELETON";
    public static final String LIVEKIT_PROVIDER = "LIVEKIT";

    public VoiceRoomToken {
        if (room == null || room.isBlank()) {
            throw new IllegalArgumentException("room is required");
        }
        if (participant == null || participant.isBlank()) {
            throw new IllegalArgumentException("participant is required");
        }
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("token is required");
        }
        if (!PROVIDER.equals(provider) && !LIVEKIT_PROVIDER.equals(provider)) {
            throw new IllegalArgumentException("provider must be " + PROVIDER + " or " + LIVEKIT_PROVIDER);
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt is required");
        }
    }
}
