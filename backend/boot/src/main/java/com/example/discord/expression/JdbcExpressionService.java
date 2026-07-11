package com.example.discord.expression;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;

final class JdbcExpressionService {
    private static final Pattern EXPRESSION_NAME = Pattern.compile("[a-z0-9_]{2,32}");
    private final DataSource dataSource;

    JdbcExpressionService(DataSource dataSource) { this.dataSource = dataSource; }

    CustomEmoji createCustomEmoji(UUID guildId, String name, String imageObjectKey, UUID creatorId) {
        uuid(guildId, "guildId"); name(name, "emoji name"); text(imageObjectKey, "imageObjectKey"); uuid(creatorId, "creatorId");
        CustomEmoji emoji = new CustomEmoji(UUID.randomUUID(), guildId, name, imageObjectKey, creatorId);
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("INSERT INTO custom_emojis (id, guild_id, name, image_object_key, creator_id) VALUES (?, ?, ?, ?, ?)")) {
            s.setObject(1, emoji.id()); s.setObject(2, guildId); s.setString(3, name); s.setString(4, imageObjectKey); s.setObject(5, creatorId); s.executeUpdate(); return emoji;
        } catch (SQLException e) { throw new IllegalStateException("failed to create custom emoji", e); }
    }
    List<CustomEmoji> customEmojis(UUID guildId) {
        uuid(guildId, "guildId"); List<CustomEmoji> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT * FROM custom_emojis WHERE guild_id = ? ORDER BY id")) {
            s.setObject(1, guildId); try (ResultSet r = s.executeQuery()) { while (r.next()) result.add(new CustomEmoji(r.getObject("id", UUID.class), guildId, r.getString("name"), r.getString("image_object_key"), r.getObject("creator_id", UUID.class))); } return result;
        } catch (SQLException e) { throw new IllegalStateException("failed to read custom emojis", e); }
    }
    Sticker createSticker(UUID guildId, String name, String description, UUID creatorId) {
        uuid(guildId, "guildId"); name(name, "sticker name"); uuid(creatorId, "creatorId"); Sticker sticker = new Sticker(UUID.randomUUID(), guildId, name, description == null ? "" : description, creatorId);
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("INSERT INTO stickers (id, guild_id, name, description, creator_id) VALUES (?, ?, ?, ?, ?)")) {
            s.setObject(1, sticker.id()); s.setObject(2, guildId); s.setString(3, name); s.setString(4, sticker.description()); s.setObject(5, creatorId); s.executeUpdate(); return sticker;
        } catch (SQLException e) { throw new IllegalStateException("failed to create sticker", e); }
    }
    List<Sticker> stickers(UUID guildId) {
        uuid(guildId, "guildId"); List<Sticker> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT * FROM stickers WHERE guild_id = ? ORDER BY id")) {
            s.setObject(1, guildId); try (ResultSet r = s.executeQuery()) { while (r.next()) result.add(new Sticker(r.getObject("id", UUID.class), guildId, r.getString("name"), r.getString("description"), r.getObject("creator_id", UUID.class))); } return result;
        } catch (SQLException e) { throw new IllegalStateException("failed to read stickers", e); }
    }
    void addReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId) {
        key(channelId, messageId, emojiKey); uuid(userId, "userId");
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("INSERT INTO message_reactions (channel_id, message_id, emoji_key, user_id) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING")) {
            s.setObject(1, channelId); s.setObject(2, messageId); s.setString(3, emojiKey); s.setObject(4, userId); s.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("failed to add reaction", e); }
    }
    void removeReaction(UUID channelId, UUID messageId, String emojiKey, UUID userId) {
        key(channelId, messageId, emojiKey); uuid(userId, "userId");
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("DELETE FROM message_reactions WHERE channel_id = ? AND message_id = ? AND emoji_key = ? AND user_id = ?")) {
            s.setObject(1, channelId); s.setObject(2, messageId); s.setString(3, emojiKey); s.setObject(4, userId); s.executeUpdate();
        } catch (SQLException e) { throw new IllegalStateException("failed to remove reaction", e); }
    }
    List<ReactionSummary> reactionSummaries(UUID channelId, UUID messageId) {
        uuid(channelId, "channelId"); uuid(messageId, "messageId"); List<ReactionSummary> result = new ArrayList<>();
        try (Connection c = dataSource.getConnection(); PreparedStatement s = c.prepareStatement("SELECT emoji_key, user_id FROM message_reactions WHERE channel_id = ? AND message_id = ? ORDER BY emoji_key, user_id")) {
            s.setObject(1, channelId); s.setObject(2, messageId); try (ResultSet r = s.executeQuery()) { String emoji = null; Set<UUID> users = new LinkedHashSet<>(); while (r.next()) { String next = r.getString("emoji_key"); if (emoji != null && !emoji.equals(next)) { result.add(new ReactionSummary(emoji, users.size(), users)); users = new LinkedHashSet<>(); } emoji = next; users.add(r.getObject("user_id", UUID.class)); } if (emoji != null) result.add(new ReactionSummary(emoji, users.size(), users)); } return result;
        } catch (SQLException e) { throw new IllegalStateException("failed to read reaction summaries", e); }
    }
    private static void key(UUID channelId, UUID messageId, String emojiKey) { uuid(channelId, "channelId"); uuid(messageId, "messageId"); text(emojiKey, "emojiKey"); }
    private static void uuid(UUID value, String label) { if (value == null) throw new IllegalArgumentException(label + " is required"); }
    private static void text(String value, String label) { if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required"); }
    private static void name(String value, String label) { text(value, label); if (!EXPRESSION_NAME.matcher(value).matches()) throw new IllegalArgumentException(label + " must be 2-32 characters of lowercase letters, numbers, or underscore"); }
}
