package com.example.discord.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryExpressionServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID MESSAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID OTHER_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");

    @Test
    void duplicateReactionAddIsIdempotentForSameUser() {
        InMemoryExpressionService service = new InMemoryExpressionService();

        service.addReaction(CHANNEL_ID, MESSAGE_ID, ":shipit:", USER_ID);
        service.addReaction(CHANNEL_ID, MESSAGE_ID, ":shipit:", USER_ID);
        service.addReaction(CHANNEL_ID, MESSAGE_ID, ":shipit:", OTHER_USER_ID);

        assertThat(service.reactionSummaries(CHANNEL_ID, MESSAGE_ID))
            .singleElement()
            .satisfies(summary -> {
                assertThat(summary.emojiKey()).isEqualTo(":shipit:");
                assertThat(summary.count()).isEqualTo(2);
                assertThat(summary.reactedBy(USER_ID)).isTrue();
            });
    }

    @Test
    void removingMissingReactionIsSafeAndOnlyRemovesOwnMembership() {
        InMemoryExpressionService service = new InMemoryExpressionService();
        service.addReaction(CHANNEL_ID, MESSAGE_ID, "wave", USER_ID);
        service.addReaction(CHANNEL_ID, MESSAGE_ID, "wave", OTHER_USER_ID);

        service.removeReaction(CHANNEL_ID, MESSAGE_ID, "wave", USER_ID);
        service.removeReaction(CHANNEL_ID, MESSAGE_ID, "wave", USER_ID);
        service.removeReaction(CHANNEL_ID, MESSAGE_ID, "missing", USER_ID);

        assertThat(service.reactionSummaries(CHANNEL_ID, MESSAGE_ID))
            .singleElement()
            .satisfies(summary -> {
                assertThat(summary.emojiKey()).isEqualTo("wave");
                assertThat(summary.count()).isEqualTo(1);
                assertThat(summary.reactedBy(USER_ID)).isFalse();
                assertThat(summary.reactedBy(OTHER_USER_ID)).isTrue();
            });
    }

    @Test
    void validatesCustomEmojiAndStickerNames() {
        InMemoryExpressionService service = new InMemoryExpressionService();

        assertThatThrownBy(() -> service.createCustomEmoji(GUILD_ID, "bad name", "emoji/shipit.png", USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("emoji name");
        assertThatThrownBy(() -> service.createSticker(GUILD_ID, "", "approved sticker", USER_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sticker name");

        CustomEmoji emoji = service.createCustomEmoji(GUILD_ID, "shipit_2026", "emoji/shipit.png", USER_ID);
        Sticker sticker = service.createSticker(GUILD_ID, "approved", "approved sticker", USER_ID);

        assertThat(service.customEmojis(GUILD_ID)).extracting(CustomEmoji::id).containsExactly(emoji.id());
        assertThat(service.stickers(GUILD_ID)).extracting(Sticker::id).containsExactly(sticker.id());
    }
}
