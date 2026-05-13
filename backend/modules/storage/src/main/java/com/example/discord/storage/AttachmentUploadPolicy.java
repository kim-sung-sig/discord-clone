package com.example.discord.storage;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record AttachmentUploadPolicy(long maxSizeBytes, Set<String> allowedContentTypes, Duration orphanTtl) {
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

    static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("attachment content type is required");
        }
        return contentType.trim().toLowerCase(Locale.ROOT);
    }
}
