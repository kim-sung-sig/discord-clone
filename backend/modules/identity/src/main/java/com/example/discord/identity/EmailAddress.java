package com.example.discord.identity;

import java.util.Locale;
import java.util.regex.Pattern;

public record EmailAddress(String value) {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public static EmailAddress from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("email address is invalid");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!EMAIL_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("email address is invalid");
        }
        return new EmailAddress(normalized);
    }

    @Override
    public String toString() {
        return value;
    }
}
