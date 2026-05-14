package com.example.discord.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryThreadServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PARENT_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID FORUM_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");

    @Test
    void archivesAndReopensThreadLifecycle() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-14T00:00:00Z"));
        InMemoryThreadService service = new InMemoryThreadService(clock);
        ThreadChannel thread = service.createThread(new CreateThreadCommand(
            GUILD_ID,
            PARENT_CHANNEL_ID,
            AUTHOR_ID,
            "support",
            ThreadType.PUBLIC,
            60
        ));

        clock.advance(Duration.ofSeconds(1));
        ThreadChannel archived = service.archive(GUILD_ID, thread.id());
        clock.advance(Duration.ofSeconds(1));
        ThreadChannel reopened = service.reopen(GUILD_ID, thread.id());

        assertThat(archived.archived()).isTrue();
        assertThat(reopened.archived()).isFalse();
        assertThat(reopened.updatedAt()).isAfter(thread.updatedAt());
    }

    @Test
    void rejectsWritesToArchivedThreadAndAllowsAfterReopen() {
        InMemoryThreadService service = new InMemoryThreadService();
        ThreadChannel thread = service.createThread(new CreateThreadCommand(
            GUILD_ID,
            PARENT_CHANNEL_ID,
            AUTHOR_ID,
            "release",
            ThreadType.PUBLIC,
            60
        ));

        service.archive(GUILD_ID, thread.id());

        assertThatThrownBy(() -> service.write(new ThreadWriteCommand(GUILD_ID, thread.id(), AUTHOR_ID, "blocked")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("archived");

        service.reopen(GUILD_ID, thread.id());
        ThreadWriteReceipt receipt = service.write(new ThreadWriteCommand(GUILD_ID, thread.id(), AUTHOR_ID, "allowed"));

        assertThat(receipt.threadId()).isEqualTo(thread.id());
        assertThat(receipt.content()).isEqualTo("allowed");
    }

    @Test
    void forumPostRequiresAllowedTag() {
        InMemoryThreadService service = new InMemoryThreadService();
        ForumTag allowedTag = service.createForumTag(GUILD_ID, FORUM_CHANNEL_ID, "help");

        assertThatThrownBy(() -> service.createForumPost(new CreateForumPostCommand(
            GUILD_ID,
            FORUM_CHANNEL_ID,
            AUTHOR_ID,
            "untagged question",
            List.of(),
            1440
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tag");

        assertThatThrownBy(() -> service.createForumPost(new CreateForumPostCommand(
            GUILD_ID,
            FORUM_CHANNEL_ID,
            AUTHOR_ID,
            "unknown tag",
            List.of(UUID.fromString("00000000-0000-0000-0000-000000000999")),
            1440
        )))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tag");

        ForumPost post = service.createForumPost(new CreateForumPostCommand(
            GUILD_ID,
            FORUM_CHANNEL_ID,
            AUTHOR_ID,
            "tagged question",
            List.of(allowedTag.id()),
            1440
        ));

        assertThat(post.tagIds()).containsExactly(allowedTag.id());
        assertThat(post.thread().parentChannelId()).isEqualTo(FORUM_CHANNEL_ID);
    }

    @Test
    void autoArchivesInactiveThreadsAfterConfiguredDuration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-14T00:00:00Z"));
        InMemoryThreadService service = new InMemoryThreadService(clock);
        ThreadChannel thread = service.createThread(new CreateThreadCommand(
            GUILD_ID,
            PARENT_CHANNEL_ID,
            AUTHOR_ID,
            "stale",
            ThreadType.PUBLIC,
            60
        ));

        clock.advance(Duration.ofMinutes(59));
        assertThat(service.archiveExpired()).isZero();
        assertThat(service.thread(GUILD_ID, thread.id()).archived()).isFalse();

        clock.advance(Duration.ofMinutes(1));
        assertThat(service.archiveExpired()).isEqualTo(1);
        assertThat(service.thread(GUILD_ID, thread.id()).archived()).isTrue();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
