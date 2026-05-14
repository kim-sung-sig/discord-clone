package com.example.discord.moderation;

import java.util.List;
import java.util.UUID;

public record OnboardingAnswer(UUID id, String label, List<UUID> roleGrantIds) {
    public OnboardingAnswer {
        if (id == null) {
            throw new IllegalArgumentException("answer id is required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("answer label is required");
        }
        roleGrantIds = roleGrantIds == null ? List.of() : List.copyOf(roleGrantIds);
    }
}
