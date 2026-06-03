package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChannelMessageQueryTest {
    @Test
    void rejectsMissingRequester() {
        assertThatThrownBy(() -> new ChannelMessageQuery(null, target(), null, 50))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("requester must not be null");
    }

    @Test
    void rejectsMissingTarget() {
        assertThatThrownBy(() -> new ChannelMessageQuery(requester(), null, null, 50))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("target must not be null");
    }

    @Test
    void rejectsNonPositiveLimit() {
        assertThatThrownBy(() -> new ChannelMessageQuery(requester(), target(), null, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("limit must be positive");
    }

    private static MessageAuthor requester() {
        return new UserMessageAuthor(UUID.randomUUID());
    }

    private static ChannelMessageTarget target() {
        return new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID());
    }
}
