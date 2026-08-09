package com.example.discord.permission;

public record AuthorizationDecision(boolean allowed, Reason reason) {
    public AuthorizationDecision {
        if (reason == null) {
            throw new NullPointerException("reason must not be null");
        }
        if (allowed && reason != Reason.ALLOWED) {
            throw new IllegalArgumentException("allowed decision must use ALLOWED reason");
        }
        if (!allowed && reason == Reason.ALLOWED) {
            throw new IllegalArgumentException("denied decision must use a denial reason");
        }
    }

    public static AuthorizationDecision allow() {
        return new AuthorizationDecision(true, Reason.ALLOWED);
    }

    public static AuthorizationDecision deny(Reason reason) {
        if (reason == Reason.ALLOWED) {
            throw new IllegalArgumentException("denied decision must use a denial reason");
        }
        return new AuthorizationDecision(false, reason);
    }

    public enum Reason {
        ALLOWED,
        MISSING_PROJECTION,
        STALE_PROJECTION,
        PERMISSION_DENIED
    }
}
