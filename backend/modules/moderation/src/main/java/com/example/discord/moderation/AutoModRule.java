package com.example.discord.moderation;

import java.util.List;
import java.util.UUID;

public record AutoModRule(
    UUID id,
    UUID guildId,
    AutoModRuleType type,
    String name,
    List<String> keywords,
    boolean enabled
) {
    public AutoModRule {
        if (id == null) {
            throw new IllegalArgumentException("rule id is required");
        }
        if (guildId == null) {
            throw new IllegalArgumentException("guildId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("rule type is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("rule name is required");
        }
        keywords = keywords == null ? List.of() : keywords.stream()
            .filter(keyword -> keyword != null && !keyword.isBlank())
            .map(keyword -> keyword.toLowerCase().trim())
            .toList();
    }
}
