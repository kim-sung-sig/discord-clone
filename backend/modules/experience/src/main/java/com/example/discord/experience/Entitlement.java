package com.example.discord.experience;

import java.util.UUID;

public record Entitlement(UUID id, UUID userId, UUID guildId, String featureKey) {
}
