package com.example.discord.moderation;

import java.util.Optional;
import java.util.UUID;

public record AutoModDecision(boolean blocked, Optional<UUID> ruleId, String reason) {
    public AutoModDecision {
        ruleId = ruleId == null ? Optional.empty() : ruleId;
    }

    public static AutoModDecision allowed() {
        return new AutoModDecision(false, Optional.empty(), null);
    }

    public static AutoModDecision blocked(UUID ruleId, String reason) {
        return new AutoModDecision(true, Optional.of(ruleId), reason);
    }
}
