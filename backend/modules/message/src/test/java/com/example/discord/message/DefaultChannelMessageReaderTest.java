package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultChannelMessageReaderTest {
    @Test
    void rejectsWhenGuardDeniesReadAccess() {
        ChannelMessageReader reader = new DefaultChannelMessageReader(
            query -> {
                throw new MessageMutationRejectedException("cannot read channel messages");
            },
            (target, beforeCursor, limit) -> new MessagePage(List.of(), null)
        );

        assertThatThrownBy(() -> reader.read(query()))
            .isInstanceOf(MessageMutationRejectedException.class)
            .hasMessage("cannot read channel messages");
    }

    @Test
    void filtersDeletedMessagesFromDefaultChannelRead() {
        Message visible = message(false);
        Message deleted = message(true);
        ChannelMessageReader reader = new DefaultChannelMessageReader(
            query -> {
            },
            (target, beforeCursor, limit) -> new MessagePage(List.of(deleted, visible), null)
        );

        MessagePage page = reader.read(query());

        assertThat(page.messages()).containsExactly(visible);
    }

    private static ChannelMessageQuery query() {
        return new ChannelMessageQuery(
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            null,
            50
        );
    }

    private static Message message(boolean deleted) {
        Instant now = Instant.parse("2026-06-03T11:00:00Z");
        return new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            deleted ? new MessageContent("[deleted]") : new MessageContent("visible"),
            List.of(),
            false,
            deleted,
            List.of(),
            now,
            now
        );
    }
}
