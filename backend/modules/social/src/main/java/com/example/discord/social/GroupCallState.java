package com.example.discord.social;

import java.util.Set;
import java.util.UUID;

public record GroupCallState(
    boolean active,
    Set<UUID> participants
) {
    public GroupCallState {
        participants = Set.copyOf(participants);
    }
}
