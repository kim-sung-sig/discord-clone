package com.example.discord.moderation;

import java.util.List;
import java.util.UUID;

public record OnboardingQuestion(UUID id, UUID guildId, String prompt, List<OnboardingAnswer> answers) {
    public OnboardingQuestion {
        if (id == null) {
            throw new IllegalArgumentException("question id is required");
        }
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("question prompt is required");
        }
        answers = answers == null ? List.of() : List.copyOf(answers);
    }
}
