package com.example.discord.expression;

import java.util.UUID;

public record Sticker(
    UUID id,
    UUID guildId,
    String name,
    String description,
    UUID creatorId
) {
}
