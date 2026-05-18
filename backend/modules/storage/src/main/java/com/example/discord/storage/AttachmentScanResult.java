package com.example.discord.storage;

public record AttachmentScanResult(AttachmentScanStatus status, String reason) {
    public AttachmentScanResult {
        if (status == null) {
            throw new IllegalArgumentException("scan status is required");
        }
        reason = reason == null ? "" : reason;
    }

    public static AttachmentScanResult clean() {
        return new AttachmentScanResult(AttachmentScanStatus.CLEAN, "");
    }

    public static AttachmentScanResult blocked(String reason) {
        return new AttachmentScanResult(AttachmentScanStatus.BLOCKED, reason);
    }

    public static AttachmentScanResult unavailable(String reason) {
        return new AttachmentScanResult(AttachmentScanStatus.UNAVAILABLE, reason);
    }
}
