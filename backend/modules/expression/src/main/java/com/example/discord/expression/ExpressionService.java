package com.example.discord.expression;

import java.util.List;
import java.util.UUID;

public interface ExpressionService {
    CustomEmoji createCustomEmoji(UUID guildId, String name, String imageObjectKey, UUID creatorId);
    List<CustomEmoji> customEmojis(UUID guildId);
    void deleteCustomEmoji(UUID guildId, UUID emojiId);
    Sticker createSticker(UUID guildId, String name, String description, UUID creatorId);
    List<Sticker> stickers(UUID guildId);
    void addReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId);
    void removeReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId);
    List<ReactionSummary> reactionSummaries(UUID channelId, UUID messageId);
}
