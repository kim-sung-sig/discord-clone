package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublishMessageRequestTest {
    @Test
    void rejectsMissingAuthor() {
        assertThatThrownBy(() -> new PublishMessageRequest(
            null,
            target(),
            new MessageContent("hello"),
            List.of(),
            new IdempotencyKey("send-1"),
            "correlation-1"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("author must not be null");
    }

    @Test
    void rejectsMissingTarget() {
        assertThatThrownBy(() -> new PublishMessageRequest(
            author(),
            null,
            new MessageContent("hello"),
            List.of(),
            new IdempotencyKey("send-1"),
            "correlation-1"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("target must not be null");
    }

    @Test
    void rejectsMissingContent() {
        assertThatThrownBy(() -> new PublishMessageRequest(
            author(),
            target(),
            null,
            List.of(),
            new IdempotencyKey("send-1"),
            "correlation-1"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("content must not be null");
    }

    @Test
    void rejectsMissingMentions() {
        assertThatThrownBy(() -> new PublishMessageRequest(
            author(),
            target(),
            new MessageContent("hello"),
            null,
            new IdempotencyKey("send-1"),
            "correlation-1"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("mentions must not be null");
    }

    @Test
    void rejectsMissingIdempotencyKey() {
        assertThatThrownBy(() -> new PublishMessageRequest(
            author(),
            target(),
            new MessageContent("hello"),
            List.of(),
            null,
            "correlation-1"
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("idempotencyKey must not be null");
    }

    @Test
    void defensivelyCopiesMentions() {
        List<MessageMentionTarget> mentions = new ArrayList<>();
        mentions.add(new UserMentionTarget(UUID.randomUUID()));

        PublishMessageRequest request = new PublishMessageRequest(
            author(),
            target(),
            new MessageContent("hello"),
            mentions,
            new IdempotencyKey("send-1"),
            "correlation-1"
        );
        mentions.clear();

        assertThat(request.mentions()).hasSize(1);
        assertThatThrownBy(() -> request.mentions().add(new SpecialMentionTarget(SpecialMentionKind.HERE)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static MessageAuthor author() {
        return new UserMessageAuthor(UUID.randomUUID());
    }

    private static MessageTarget target() {
        return new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID());
    }
}
