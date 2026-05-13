package com.example.discord.user;

import java.util.Locale;
import java.util.regex.Pattern;

public record Username(String value) {
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-z0-9_](?:[a-z0-9_.]{1,30}[a-z0-9_])$");

    public static Username from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("username is invalid");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("username is invalid");
        }
        return new Username(normalized);
    }

    @Override
    public String toString() {
        return value;
    }
}
