package com.example.discord.user;

import java.time.Instant;
import java.util.UUID;

public record UserProfile(
    UUID id,
    Username username,
    String displayName,
    Instant createdAt,
    PrivacySettings privacy
) {
    public static UserProfile create(UUID id, Username username, String displayName, Instant createdAt) {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("display name must not be blank");
        }
        return new UserProfile(id, username, displayName.trim(), createdAt, PrivacySettings.defaults());
    }
}
