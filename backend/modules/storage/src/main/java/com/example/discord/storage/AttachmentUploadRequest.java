package com.example.discord.storage;

import java.util.UUID;

public record AttachmentUploadRequest(
    UUID ownerId,
    UUID guildId,
    UUID channelId,
    String filename,
    String contentType,
    long sizeBytes
) {
}
