package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MessageAuthorTest {
    @Test
    void rejectsUserAuthorWithoutUserId() {
        assertThatThrownBy(() -> new UserMessageAuthor(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("userId must not be null");
    }

    @Test
    void rejectsBotAuthorWithoutBotId() {
        assertThatThrownBy(() -> new BotMessageAuthor(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("botId must not be null");
    }

    @Test
    void rejectsWebhookAuthorWithoutWebhookId() {
        assertThatThrownBy(() -> new WebhookMessageAuthor(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("webhookId must not be null");
    }

    @Test
    void rejectsSystemAuthorWithoutReason() {
        assertThatThrownBy(() -> new SystemMessageAuthor(" "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("system author reason must not be blank");
    }
}
