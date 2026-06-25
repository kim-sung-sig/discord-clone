package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EditMessageRequestTest {
    @Test
    void rejectsMissingMessageId() {
        assertThatThrownBy(() -> new EditMessageRequest(
            null,
            editor(),
            new MessageContent("updated"),
            List.of()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("messageId must not be null");
    }

    @Test
    void rejectsMissingEditor() {
        assertThatThrownBy(() -> new EditMessageRequest(
            UUID.randomUUID(),
            null,
            new MessageContent("updated"),
            List.of()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("editor must not be null");
    }

    @Test
    void rejectsMissingContent() {
        assertThatThrownBy(() -> new EditMessageRequest(
            UUID.randomUUID(),
            editor(),
            null,
            List.of()
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("content must not be null");
    }

    @Test
    void rejectsMissingMentions() {
        assertThatThrownBy(() -> new EditMessageRequest(
            UUID.randomUUID(),
            editor(),
            new MessageContent("updated"),
            null
        ))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("mentions must not be null");
    }

    @Test
    void defensivelyCopiesMentions() {
        List<MessageMentionTarget> mentions = new ArrayList<>();
        mentions.add(new SpecialMentionTarget(SpecialMentionKind.EVERYONE));

        EditMessageRequest request = new EditMessageRequest(
            UUID.randomUUID(),
            editor(),
            new MessageContent("updated"),
            mentions
        );
        mentions.clear();

        assertThat(request.mentions()).hasSize(1);
        assertThatThrownBy(() -> request.mentions().add(new SpecialMentionTarget(SpecialMentionKind.HERE)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    private static MessageAuthor editor() {
        return new UserMessageAuthor(UUID.randomUUID());
    }
}
