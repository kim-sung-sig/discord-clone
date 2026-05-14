package com.example.discord.experience;

import java.util.UUID;

public record SoundboardSound(UUID id, UUID guildId, String name, String objectKey, UUID creatorId) {
}
