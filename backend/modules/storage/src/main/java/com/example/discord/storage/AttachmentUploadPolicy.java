package com.example.discord.storage;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record AttachmentUploadPolicy(long maxSizeBytes, Set<String> allowedContentTypes, Duration orphanTtl) {
    private static final Map<String, String> EXTENSION_CONTENT_TYPES = Map.of(
        ".png", "image/png",
        ".jpg", "image/jpeg",
        ".jpeg", "image/jpeg"
    );

    public AttachmentUploadPolicy {
        if (maxSizeBytes < 1) {
            throw new IllegalArgumentException("max size must be positive");
        }
        if (allowedContentTypes == null || allowedContentTypes.isEmpty()) {
            throw new IllegalArgumentException("allowed content types are required");
        }
        allowedContentTypes = Set.copyOf(allowedContentTypes.stream()
            .map(AttachmentUploadPolicy::normalizeContentType)
            .toList());
        Objects.requireNonNull(orphanTtl, "orphanTtl must not be null");
        if (orphanTtl.isNegative() || orphanTtl.isZero()) {
            throw new IllegalArgumentException("orphan ttl must be positive");
        }
    }

    void validate(String contentType, long sizeBytes) {
        if (sizeBytes < 1) {
            throw new IllegalArgumentException("attachment size is required");
        }
        if (sizeBytes > maxSizeBytes) {
            throw new IllegalArgumentException("attachment size exceeds policy");
        }
        if (!allowedContentTypes.contains(normalizeContentType(contentType))) {
            throw new IllegalArgumentException("attachment content type is not allowed");
        }
    }

    void validateFilename(String filename, String contentType) {
        String extension = extension(filename);
        if (extension.isEmpty()) {
            return;
        }
        String expectedContentType = EXTENSION_CONTENT_TYPES.get(extension);
        if (expectedContentType != null && !expectedContentType.equals(normalizeContentType(contentType))) {
            throw new IllegalArgumentException("attachment filename extension does not match content type");
        }
    }

    static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("attachment content type is required");
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private static String extension(String filename) {
        if (filename == null) {
            return "";
        }
        String normalized = filename.trim().toLowerCase(Locale.ROOT);
        int dot = normalized.lastIndexOf('.');
        if (dot < 0 || dot == normalized.length() - 1) {
            return "";
        }
        return normalized.substring(dot);
    }
}
