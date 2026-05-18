package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InMemoryMessageServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID MENTION_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @Test
    void returnsNewestMessagesBeforeOpaqueCursor() {
        InMemoryMessageService service = service();
        Message first = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "first"));
        Message second = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "second"));
        Message third = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "third"));

        MessagePage firstPage = service.messages(GUILD_ID, CHANNEL_ID, null, 2);
        MessagePage secondPage = service.messages(GUILD_ID, CHANNEL_ID, firstPage.nextCursor(), 2);

        assertThat(firstPage.messages()).extracting(Message::id).containsExactly(third.id(), second.id());
        assertThat(firstPage.nextCursor()).isNotBlank();
        assertThat(secondPage.messages()).extracting(Message::id).containsExactly(first.id());
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void extractsUuidMentionsFromDiscordStyleTokens() {
        InMemoryMessageService service = service();

        Message message = service.create(new CreateMessageCommand(
            GUILD_ID,
            CHANNEL_ID,
            AUTHOR_ID,
            "hello <@" + MENTION_ID + "> and @alice-dev and <@not-a-uuid>"
        ));

        assertThat(message.mentions()).containsExactly(MENTION_ID.toString(), "alice-dev");
    }

    @Test
    void recordsEditHistoryWhenContentChanges() {
        InMemoryMessageService service = service();
        Message original = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "before"));

        Message edited = service.edit(new EditMessageCommand(GUILD_ID, CHANNEL_ID, original.id(), "after"));

        assertThat(edited.content()).isEqualTo("after");
        assertThat(edited.edited()).isTrue();
        assertThat(edited.editHistory()).hasSize(1);
        assertThat(edited.editHistory().getFirst().content()).isEqualTo("before");
    }

    @Test
    void deleteLeavesTombstoneWithoutOriginalContent() {
        InMemoryMessageService service = service();
        Message original = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "sensitive text"));

        Message deleted = service.delete(GUILD_ID, CHANNEL_ID, original.id());

        assertThat(deleted.deleted()).isTrue();
        assertThat(deleted.content()).isEmpty();
        assertThat(service.messages(GUILD_ID, CHANNEL_ID, null, 10).messages())
            .singleElement()
            .extracting(Message::deleted)
            .isEqualTo(true);
    }

    @Test
    void deleteClearsEditHistoryToAvoidLeakingPriorContent() {
        InMemoryMessageService service = service();
        Message original = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "secret before edit"));
        Message edited = service.edit(new EditMessageCommand(GUILD_ID, CHANNEL_ID, original.id(), "safe replacement"));

        Message deleted = service.delete(GUILD_ID, CHANNEL_ID, edited.id());

        assertThat(deleted.content()).isEmpty();
        assertThat(deleted.editHistory()).isEmpty();
    }

    @Test
    void pinsAndUnpinsMessages() {
        InMemoryMessageService service = service();
        Message original = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "pin me"));

        Message pinned = service.pin(GUILD_ID, CHANNEL_ID, original.id());
        Message unpinned = service.unpin(GUILD_ID, CHANNEL_ID, original.id());

        assertThat(pinned.pinned()).isTrue();
        assertThat(unpinned.pinned()).isFalse();
    }

    @Test
    void searchesVisibleNonDeletedMessagesByContent() {
        InMemoryMessageService service = service();
        Message match = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "ship the backend core"));
        Message deleted = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "backend secret"));
        service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "frontend shell"));
        service.delete(GUILD_ID, CHANNEL_ID, deleted.id());

        assertThat(service.search(GUILD_ID, CHANNEL_ID, "BACKEND", 10))
            .extracting(Message::id)
            .containsExactly(match.id());
    }

    @Test
    void guildSearchOnlyReturnsMessagesFromAllowedChannels() {
        InMemoryMessageService service = service();
        UUID allowedChannelId = CHANNEL_ID;
        UUID hiddenChannelId = UUID.fromString("00000000-0000-0000-0000-000000000005");
        Message visible = service.create(new CreateMessageCommand(GUILD_ID, allowedChannelId, AUTHOR_ID, "release search notes"));
        service.create(new CreateMessageCommand(GUILD_ID, hiddenChannelId, AUTHOR_ID, "hidden search notes"));

        assertThat(service.search(GUILD_ID, Set.of(allowedChannelId), "search", 10))
            .extracting(Message::id)
            .containsExactly(visible.id());
    }

    @Test
    void guildSearchExcludesDeletedMessages() {
        InMemoryMessageService service = service();
        Message visible = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "moderation search live"));
        Message deleted = service.create(new CreateMessageCommand(GUILD_ID, CHANNEL_ID, AUTHOR_ID, "moderation search deleted"));
        service.delete(GUILD_ID, CHANNEL_ID, deleted.id());

        assertThat(service.search(GUILD_ID, Set.of(CHANNEL_ID), "moderation", 10))
            .extracting(Message::id)
            .containsExactly(visible.id());
    }

    private static InMemoryMessageService service() {
        AtomicInteger ticks = new AtomicInteger();
        Clock clock = new IncrementingClock(Instant.parse("2026-05-13T00:00:00Z"), ticks);
        return new InMemoryMessageService(clock);
    }

    private static final class IncrementingClock extends Clock {
        private final Instant base;
        private final AtomicInteger ticks;

        private IncrementingClock(Instant base, AtomicInteger ticks) {
            this.base = base;
            this.ticks = ticks;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return base.plusSeconds(ticks.getAndIncrement());
        }
    }
}
