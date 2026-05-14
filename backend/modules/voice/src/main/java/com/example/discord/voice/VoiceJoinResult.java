package com.example.discord.voice;

public record VoiceJoinResult(VoiceParticipantState participant, VoiceRoomToken token) {
    public VoiceJoinResult {
        if (participant == null) {
            throw new IllegalArgumentException("participant is required");
        }
        if (token == null) {
            throw new IllegalArgumentException("token is required");
        }
    }
}
