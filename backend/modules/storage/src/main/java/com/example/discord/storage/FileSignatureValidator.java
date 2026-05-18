package com.example.discord.storage;

import java.util.Arrays;
import java.util.Map;

final class FileSignatureValidator {
    private static final Map<String, byte[]> SIGNATURES = Map.of(
        "image/png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A},
        "image/jpeg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}
    );

    void validate(String contentType, byte[] bytes) {
        byte[] signature = SIGNATURES.get(AttachmentUploadPolicy.normalizeContentType(contentType));
        if (signature == null) {
            throw new IllegalArgumentException("attachment file signature is not supported");
        }
        if (bytes == null || bytes.length < signature.length) {
            throw new IllegalArgumentException("attachment file signature does not match content type");
        }
        if (!Arrays.equals(signature, Arrays.copyOf(bytes, signature.length))) {
            throw new IllegalArgumentException("attachment file signature does not match content type");
        }
    }
}
