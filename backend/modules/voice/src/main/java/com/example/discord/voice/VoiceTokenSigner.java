package com.example.discord.voice;

public interface VoiceTokenSigner {
    VoiceRoomToken sign(VoiceTokenSigningRequest request);
}
