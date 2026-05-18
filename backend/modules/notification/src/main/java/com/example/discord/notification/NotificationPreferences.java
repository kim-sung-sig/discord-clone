package com.example.discord.notification;

public record NotificationPreferences(
    boolean mentionsEnabled,
    boolean directMessagesEnabled,
    boolean serverNotificationsEnabled
) {
    public static NotificationPreferences defaults() {
        return new NotificationPreferences(true, true, true);
    }

    boolean enabled(NotificationKind kind) {
        return switch (kind) {
            case MENTION -> mentionsEnabled;
            case DM -> directMessagesEnabled;
            case SERVER -> serverNotificationsEnabled;
        };
    }
}
