package com.example.discord.voice;

public final class SkeletonLiveKitTokenSigner implements VoiceTokenSigner {
    @Override
    public VoiceRoomToken sign(VoiceTokenSigningRequest request) {
        return new VoiceRoomToken(
            "voice:%s:%s".formatted(request.guildId(), request.channelId()),
            request.userId().toString(),
            "NON_PRODUCTION_LIVEKIT_SKELETON:%s:%s:%s".formatted(
                request.guildId(),
                request.channelId(),
                request.userId()
            ),
            VoiceRoomToken.PROVIDER,
            request.issuedAt().plusSeconds(request.ttlSeconds())
        );
    }
}
