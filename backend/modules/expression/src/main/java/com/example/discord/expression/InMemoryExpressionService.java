package com.example.discord.expression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class InMemoryExpressionService {
    private static final Pattern EXPRESSION_NAME = Pattern.compile("[a-z0-9_]{2,32}");

    private final Map<UUID, CustomEmoji> customEmojis = new LinkedHashMap<>();
    private final Map<UUID, Sticker> stickers = new LinkedHashMap<>();
    private final Map<ReactionKey, LinkedHashSet<UUID>> reactions = new LinkedHashMap<>();

    public synchronized CustomEmoji createCustomEmoji(UUID guildId, String name, String imageObjectKey, UUID creatorId) {
        requireUuid(guildId, "guildId");
        requireExpressionName(name, "emoji name");
        requireText(imageObjectKey, "imageObjectKey");
        requireUuid(creatorId, "creatorId");

        CustomEmoji emoji = new CustomEmoji(UUID.randomUUID(), guildId, name, imageObjectKey, creatorId);
        customEmojis.put(emoji.id(), emoji);
        return emoji;
    }

    public synchronized List<CustomEmoji> customEmojis(UUID guildId) {
        requireUuid(guildId, "guildId");
        return customEmojis.values().stream()
            .filter(emoji -> emoji.guildId().equals(guildId))
            .toList();
    }

    public synchronized void deleteCustomEmoji(UUID guildId, UUID emojiId) {
        CustomEmoji emoji = customEmoji(guildId, emojiId);
        customEmojis.remove(emoji.id());
    }

    public synchronized Sticker createSticker(UUID guildId, String name, String description, UUID creatorId) {
        requireUuid(guildId, "guildId");
        requireExpressionName(name, "sticker name");
        requireUuid(creatorId, "creatorId");

        Sticker sticker = new Sticker(UUID.randomUUID(), guildId, name, description == null ? "" : description, creatorId);
        stickers.put(sticker.id(), sticker);
        return sticker;
    }

    public synchronized List<Sticker> stickers(UUID guildId) {
        requireUuid(guildId, "guildId");
        return stickers.values().stream()
            .filter(sticker -> sticker.guildId().equals(guildId))
            .toList();
    }

    public synchronized void addReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId) {
        ReactionKey key = reactionKey(channelId, messageId, emojiKey);
        requireUuid(userId, "userId");
        reactions.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(userId);
    }

    public synchronized void removeReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId) {
        ReactionKey key = reactionKey(channelId, messageId, emojiKey);
        requireUuid(userId, "userId");
        Set<UUID> userIds = reactions.get(key);
        if (userIds == null) {
            return;
        }
        userIds.remove(userId);
        if (userIds.isEmpty()) {
            reactions.remove(key);
        }
    }

    public synchronized List<ReactionSummary> reactionSummaries(UUID channelId, UUID messageId) {
        requireUuid(channelId, "channelId");
        requireUuid(messageId, "messageId");

        List<ReactionSummary> summaries = new ArrayList<>();
        for (Map.Entry<ReactionKey, LinkedHashSet<UUID>> entry : reactions.entrySet()) {
            ReactionKey key = entry.getKey();
            if (key.channelId().equals(channelId) && key.messageId().equals(messageId) && !entry.getValue().isEmpty()) {
                summaries.add(new ReactionSummary(key.emojiKey(), entry.getValue().size(), entry.getValue()));
            }
        }
        summaries.sort(Comparator.comparing(ReactionSummary::emojiKey));
        return List.copyOf(summaries);
    }

    private CustomEmoji customEmoji(UUID guildId, UUID emojiId) {
        requireUuid(guildId, "guildId");
        requireUuid(emojiId, "emojiId");
        CustomEmoji emoji = customEmojis.get(emojiId);
        if (emoji == null || !emoji.guildId().equals(guildId)) {
            throw new ExpressionNotFoundException("custom emoji not found");
        }
        return emoji;
    }

    private static ReactionKey reactionKey(UUID channelId, UUID messageId, String emojiKey) {
        requireUuid(channelId, "channelId");
        requireUuid(messageId, "messageId");
        requireText(emojiKey, "emojiKey");
        return new ReactionKey(channelId, messageId, emojiKey);
    }

    private static void requireUuid(UUID value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
    }

    private static void requireExpressionName(String value, String label) {
        requireText(value, label);
        if (!EXPRESSION_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be 2-32 characters of lowercase letters, numbers, or underscore");
        }
    }

    private record ReactionKey(UUID channelId, UUID messageId, String emojiKey) {
    }
}
