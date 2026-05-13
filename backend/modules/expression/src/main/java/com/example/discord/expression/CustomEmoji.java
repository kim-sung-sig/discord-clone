package com.example.discord.expression;

import java.util.UUID;

public record CustomEmoji(
    UUID id,
    UUID guildId,
    String name,
    String imageObjectKey,
    UUID creatorId
) {
}
