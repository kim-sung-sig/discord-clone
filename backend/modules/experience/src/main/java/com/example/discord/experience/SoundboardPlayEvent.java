package com.example.discord.experience;

import java.util.UUID;

public record SoundboardPlayEvent(UUID id, UUID channelId, UUID soundId, UUID userId) {
}
