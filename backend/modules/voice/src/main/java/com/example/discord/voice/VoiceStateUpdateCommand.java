package com.example.discord.voice;

import java.util.UUID;

public record VoiceStateUpdateCommand(
    UUID guildId,
    UUID channelId,
    UUID userId,
    boolean muted,
    boolean deafened,
    boolean speaking,
    boolean screenSharing
) {
    public VoiceStateUpdateCommand {
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (channelId == null) {
            throw new IllegalArgumentException("channelId is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId is required");
        }
    }
}
