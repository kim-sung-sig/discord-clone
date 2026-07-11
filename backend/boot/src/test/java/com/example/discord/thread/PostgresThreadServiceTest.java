package com.example.discord.thread;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("postgres")
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PostgresThreadServiceTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID PARENT_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000102");
    private static final UUID FORUM_CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final UUID AUTHOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000104");

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM thread_post_tags");
            statement.executeUpdate("DELETE FROM forum_tags");
            statement.executeUpdate("DELETE FROM thread_messages");
            statement.executeUpdate("DELETE FROM threads");
        }
    }

    @Test
    void persistsThreadLifecycleWritesForumTagsAndExpiration() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-14T00:00:00Z"));
        JdbcThreadService service = new JdbcThreadService(dataSource, clock);
        ThreadChannel thread = service.createThread(new CreateThreadCommand(
            GUILD_ID, PARENT_CHANNEL_ID, AUTHOR_ID, "support", ThreadType.PUBLIC, 60
        ));

        clock.advance(Duration.ofSeconds(1));
        assertThat(service.archive(GUILD_ID, thread.id()).archived()).isTrue();
        clock.advance(Duration.ofSeconds(1));
        assertThat(service.reopen(GUILD_ID, thread.id()).archived()).isFalse();
        assertThat(service.write(new ThreadWriteCommand(GUILD_ID, thread.id(), AUTHOR_ID, "allowed")).threadId())
            .isEqualTo(thread.id());

        ForumTag tag = service.createForumTag(GUILD_ID, FORUM_CHANNEL_ID, "help");
        ForumPost post = service.createForumPost(new CreateForumPostCommand(
            GUILD_ID, FORUM_CHANNEL_ID, AUTHOR_ID, "tagged question", List.of(tag.id()), 60
        ));
        assertThat(post.tagIds()).containsExactly(tag.id());

        clock.advance(Duration.ofMinutes(60));
        assertThat(service.archiveExpired()).isEqualTo(2);
        assertThat(service.thread(GUILD_ID, thread.id()).archived()).isTrue();
    }

    @Test
    void preservesArchivedWriteAndForumTagValidation() {
        JdbcThreadService service = new JdbcThreadService(dataSource, Clock.systemUTC());
        ThreadChannel thread = service.createThread(new CreateThreadCommand(
            GUILD_ID, PARENT_CHANNEL_ID, AUTHOR_ID, "release", ThreadType.PUBLIC, 60
        ));
        service.archive(GUILD_ID, thread.id());

        assertThatThrownBy(() -> service.write(new ThreadWriteCommand(GUILD_ID, thread.id(), AUTHOR_ID, "blocked")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("archived");
        assertThatThrownBy(() -> service.createForumPost(new CreateForumPostCommand(
            GUILD_ID, FORUM_CHANNEL_ID, AUTHOR_ID, "untagged", List.of(), 60
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("tag");
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
