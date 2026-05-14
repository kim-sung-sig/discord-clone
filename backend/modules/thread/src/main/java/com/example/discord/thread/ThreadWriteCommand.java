package com.example.discord.thread;

import java.util.UUID;

public record ThreadWriteCommand(UUID guildId, UUID threadId, UUID authorId, String content) {
}
