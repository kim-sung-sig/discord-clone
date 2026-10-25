package com.example.discord.auth;

import com.example.discord.message.MessageAuthorDirectory;
import com.example.discord.user.UserProfile;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Adapts the user profile store to the message module's author name port, so the message module never reads
 * user storage directly.
 */
@Component
class AuthStoreMessageAuthorDirectory implements MessageAuthorDirectory {
    private final AuthStore store;

    AuthStoreMessageAuthorDirectory(AuthStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public Map<UUID, String> displayNames(Set<UUID> userIds) {
        Objects.requireNonNull(userIds, "userIds must not be null");
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (UserProfile profile : store.findProfilesByIds(Set.copyOf(userIds))) {
            names.put(profile.id(), displayNameOf(profile));
        }
        return Map.copyOf(names);
    }

    static String displayNameOf(UserProfile profile) {
        String displayName = profile.displayName();
        if (displayName != null && !displayName.isBlank()) {
            return displayName.trim();
        }
        return profile.username().value();
    }
}
