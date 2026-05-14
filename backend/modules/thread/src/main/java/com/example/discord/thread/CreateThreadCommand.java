package com.example.discord.thread;

import java.util.UUID;

public record CreateThreadCommand(
    UUID guildId,
    UUID parentChannelId,
    UUID ownerId,
    String name,
    ThreadType type,
    int autoArchiveMinutes
) {
}
