package com.example.discord.storage;

import java.time.Instant;
import java.util.UUID;

public record Attachment(
    UUID id,
    UUID ownerId,
    UUID guildId,
    UUID channelId,
    UUID messageId,
    String filename,
    String contentType,
    long sizeBytes,
    String objectKey,
    AttachmentStatus status,
    Instant createdAt,
    Instant updatedAt
) {
}
