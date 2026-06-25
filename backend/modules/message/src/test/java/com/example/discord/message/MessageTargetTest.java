package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageTargetTest {
    @Test
    void rejectsChannelTargetWithoutGuildId() {
        UUID channelId = UUID.randomUUID();

        assertThatThrownBy(() -> new ChannelMessageTarget(null, channelId))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("guildId must not be null");
    }

    @Test
    void rejectsChannelTargetWithoutChannelId() {
        UUID guildId = UUID.randomUUID();

        assertThatThrownBy(() -> new ChannelMessageTarget(guildId, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("channelId must not be null");
    }

    @Test
    void rejectsDirectTargetWithoutConversationId() {
        UUID recipientId = UUID.randomUUID();

        assertThatThrownBy(() -> new DirectMessageTarget(null, recipientId))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("conversationId must not be null");
    }

    @Test
    void rejectsDirectTargetWithoutRecipientId() {
        UUID conversationId = UUID.randomUUID();

        assertThatThrownBy(() -> new DirectMessageTarget(conversationId, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("recipientId must not be null");
    }

    @Test
    void rejectsThreadTargetWithoutGuildId() {
        UUID channelId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();

        assertThatThrownBy(() -> new ThreadMessageTarget(null, channelId, threadId))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("guildId must not be null");
    }

    @Test
    void rejectsThreadTargetWithoutChannelId() {
        UUID guildId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();

        assertThatThrownBy(() -> new ThreadMessageTarget(guildId, null, threadId))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("channelId must not be null");
    }

    @Test
    void rejectsThreadTargetWithoutThreadId() {
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        assertThatThrownBy(() -> new ThreadMessageTarget(guildId, channelId, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("threadId must not be null");
    }
}
