package com.example.discord.message;

import java.util.UUID;

public record CreateMessageCommand(UUID guildId, UUID channelId, UUID authorId, String content) {
}
