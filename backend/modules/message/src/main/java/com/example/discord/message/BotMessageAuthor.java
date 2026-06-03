package com.example.discord.message;

import java.util.Objects;
import java.util.UUID;

public record BotMessageAuthor(UUID botId) implements MessageAuthor {
    public BotMessageAuthor {
        Objects.requireNonNull(botId, "botId must not be null");
    }
}
