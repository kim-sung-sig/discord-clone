package com.example.discord.storage;

@FunctionalInterface
public interface AttachmentScanner {
    AttachmentScanResult scan(Attachment attachment, byte[] bytes);

    static AttachmentScanner allowAll() {
        return (attachment, bytes) -> AttachmentScanResult.clean();
    }
}
