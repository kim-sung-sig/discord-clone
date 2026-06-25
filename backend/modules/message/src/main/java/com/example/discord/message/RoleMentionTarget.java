package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record RoleMentionTarget(UUID roleId) implements MessageMentionTarget {
    public RoleMentionTarget {
        Objects.requireNonNull(roleId, "roleId must not be null");
    }
}
