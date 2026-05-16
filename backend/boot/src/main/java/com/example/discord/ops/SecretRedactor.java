package com.example.discord.ops;

import java.util.regex.Pattern;

public final class SecretRedactor {
    private static final Pattern BEARER_TOKEN = Pattern.compile("(?i)(Authorization\\s*:\\s*Bearer\\s+)[^\\s\"']+");
    private static final Pattern KEY_VALUE_SECRET = Pattern.compile(
        "(?i)([A-Z0-9_.-]*(?:PASSWORD|TOKEN|SECRET|API_KEY)[A-Z0-9_.-]*\\s*[=:]\\s*)([^\\s\"']+)"
    );
    private static final Pattern JDBC_PASSWORD = Pattern.compile("(?i)([?&]password=)[^&\\s\"']+");

    private SecretRedactor() {
    }

    public static String redact(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String redacted = BEARER_TOKEN.matcher(value).replaceAll("$1<redacted>");
        redacted = KEY_VALUE_SECRET.matcher(redacted).replaceAll("$1<redacted>");
        return JDBC_PASSWORD.matcher(redacted).replaceAll("$1<redacted>");
    }
}
