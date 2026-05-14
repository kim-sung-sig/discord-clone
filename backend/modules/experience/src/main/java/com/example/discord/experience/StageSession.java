package com.example.discord.experience;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public record StageSession(
    UUID id,
    UUID guildId,
    UUID channelId,
    String topic,
    Set<UUID> moderatorIds,
    Set<UUID> speakerIds,
    Set<UUID> audienceIds,
    Set<UUID> pendingSpeakerIds
) {
    public StageSession {
        moderatorIds = copyOf(moderatorIds);
        speakerIds = copyOf(speakerIds);
        audienceIds = copyOf(audienceIds);
        pendingSpeakerIds = copyOf(pendingSpeakerIds);
    }

    private static Set<UUID> copyOf(Set<UUID> values) {
        return Set.copyOf(new LinkedHashSet<>(values == null ? Set.of() : values));
    }
}
