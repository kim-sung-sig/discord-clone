package com.example.discord.gateway;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GatewayPayloadSanitizer {
    private GatewayPayloadSanitizer() {
    }

    public static Map<String, Object> sanitize(Map<String, Object> payload) {
        Map<String, Object> sanitized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            if (isSensitiveKey(entry.getKey()) || isSensitiveValue(entry.getValue())) {
                continue;
            }
            sanitized.put(entry.getKey(), sanitizeValue(entry.getValue()));
        }
        return Map.copyOf(sanitized);
    }

    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> nested = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (!(entry.getKey() instanceof String key) || isSensitiveKey(key) || isSensitiveValue(entry.getValue())) {
                    continue;
                }
                nested.put(key, sanitizeValue(entry.getValue()));
            }
            return Map.copyOf(nested);
        }
        if (value instanceof List<?> listValue) {
            return listValue.stream()
                .filter(item -> !isSensitiveValue(item))
                .map(GatewayPayloadSanitizer::sanitizeValue)
                .toList();
        }
        return value;
    }

    private static boolean isSensitiveKey(String key) {
        String normalized = key == null ? "" : key.replace("-", "").replace("_", "").toLowerCase();
        return normalized.contains("token")
            || normalized.contains("secret")
            || normalized.contains("password")
            || normalized.contains("authorization")
            || normalized.contains("cookie")
            || normalized.contains("signedurl")
            || normalized.contains("objectkey")
            || normalized.contains("storagekey");
    }

    private static boolean isSensitiveValue(Object value) {
        if (!(value instanceof String text)) {
            return false;
        }
        String normalized = text.toLowerCase();
        return normalized.contains("x-amz-signature")
            || normalized.contains("x-goog-signature")
            || normalized.contains("x-ms-signature")
            || normalized.contains("sig=");
    }
}
