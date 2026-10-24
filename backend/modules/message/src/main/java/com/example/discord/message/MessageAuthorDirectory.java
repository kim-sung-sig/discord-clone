package com.example.discord.message;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Outbound port that resolves user message author display names for read responses and Gateway payloads.
 * The message module does not read user storage; the owner of user profiles implements this port.
 */
@FunctionalInterface
public interface MessageAuthorDirectory {
    /**
     * Resolves display names in one bounded lookup.
     *
     * @param userIds author user ids, typically the distinct authors of one message page
     * @return the display name (falling back to the username) keyed by user id; users that no longer exist are absent
     */
    Map<UUID, String> displayNames(Set<UUID> userIds);

    default Optional<String> displayName(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return Optional.ofNullable(displayNames(Set.of(userId)).get(userId));
    }
}
