package com.example.discord.message;

import java.util.Objects;

public record SpecialMentionTarget(SpecialMentionKind kind) implements MessageMentionTarget {
    public SpecialMentionTarget {
        Objects.requireNonNull(kind, "kind must not be null");
    }
}
