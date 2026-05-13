package com.example.discord.message;

import java.util.UUID;

public record EditMessageCommand(UUID guildId, UUID channelId, UUID messageId, String content) {
}
