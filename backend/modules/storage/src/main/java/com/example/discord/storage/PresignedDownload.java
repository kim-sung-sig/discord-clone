package com.example.discord.storage;

import java.util.UUID;

public record PresignedDownload(UUID attachmentId, String objectKey, String downloadUrl) {
}
