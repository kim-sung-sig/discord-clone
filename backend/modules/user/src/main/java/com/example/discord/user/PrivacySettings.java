package com.example.discord.user;

public record PrivacySettings(
    boolean allowDirectMessagesFromMutualGuildMembers,
    boolean allowFriendRequests
) {
    public static PrivacySettings defaults() {
        return new PrivacySettings(true, true);
    }
}
