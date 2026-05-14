package com.example.discord.experience;

import java.util.UUID;

public record StageSpeakRequest(UUID sessionId, UUID userId) {
}
