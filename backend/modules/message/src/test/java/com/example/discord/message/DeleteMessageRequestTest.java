package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeleteMessageRequestTest {
    @Test
    void rejectsMissingMessageId() {
        assertThatThrownBy(() -> new DeleteMessageRequest(null, requester()))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("messageId must not be null");
    }

    @Test
    void rejectsMissingRequester() {
        assertThatThrownBy(() -> new DeleteMessageRequest(UUID.randomUUID(), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("requester must not be null");
    }

    private static MessageAuthor requester() {
        return new UserMessageAuthor(UUID.randomUUID());
    }
}
