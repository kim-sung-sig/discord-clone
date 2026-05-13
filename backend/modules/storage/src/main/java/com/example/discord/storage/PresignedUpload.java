package com.example.discord.storage;

import java.util.UUID;

public record PresignedUpload(UUID attachmentId, String objectKey, String uploadUrl) {
}
