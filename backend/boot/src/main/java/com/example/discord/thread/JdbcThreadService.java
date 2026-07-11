package com.example.discord.thread;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("postgres")
class JdbcThreadService {
    private final DataSource dataSource;
    private final Clock clock;

    JdbcThreadService(DataSource dataSource, Clock clock) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    ThreadChannel createThread(CreateThreadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        require(command.guildId(), "guildId must not be null");
        require(command.parentChannelId(), "parentChannelId must not be null");
        require(command.ownerId(), "ownerId must not be null");
        if (command.type() == null) throw new NullPointerException("thread type must not be null");
        if (command.autoArchiveMinutes() < 1) throw new IllegalArgumentException("auto archive minutes must be positive");
        String name = text(command.name(), "thread name is required");
        Instant now = clock.instant();
        ThreadChannel thread = new ThreadChannel(UUID.randomUUID(), command.guildId(), command.parentChannelId(), command.ownerId(), name,
            command.type(), false, command.autoArchiveMinutes(), now, now, now);
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO threads (id, guild_id, parent_channel_id, owner_id, name, type, archived, auto_archive_minutes, last_activity_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            bindThread(statement, thread);
            statement.executeUpdate();
            return thread;
        } catch (SQLException exception) { throw new IllegalStateException("failed to create thread", exception); }
    }

    ForumTag createForumTag(UUID guildId, UUID forumChannelId, String name) {
        require(guildId, "guildId must not be null"); require(forumChannelId, "forumChannelId must not be null");
        ForumTag tag = new ForumTag(UUID.randomUUID(), guildId, forumChannelId, text(name, "forum tag name is required"));
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO forum_tags (id, guild_id, forum_channel_id, name) VALUES (?, ?, ?, ?)")) {
            statement.setObject(1, tag.id()); statement.setObject(2, guildId); statement.setObject(3, forumChannelId); statement.setString(4, tag.name());
            statement.executeUpdate(); return tag;
        } catch (SQLException exception) { throw new IllegalStateException("failed to create forum tag", exception); }
    }

    ForumPost createForumPost(CreateForumPostCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        require(command.guildId(), "guildId must not be null"); require(command.forumChannelId(), "forumChannelId must not be null"); require(command.authorId(), "authorId must not be null");
        if (command.tagIds() == null || command.tagIds().isEmpty()) throw new IllegalArgumentException("forum post requires at least one allowed tag");
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (UUID tagId : command.tagIds()) if (!allowedTag(connection, tagId, command.guildId(), command.forumChannelId())) throw new IllegalArgumentException("forum post requires allowed tag");
                ThreadChannel thread = createThread(connection, new CreateThreadCommand(command.guildId(), command.forumChannelId(), command.authorId(), command.title(), ThreadType.PUBLIC, command.autoArchiveMinutes()));
                try (PreparedStatement statement = connection.prepareStatement("INSERT INTO thread_post_tags (thread_id, tag_id) VALUES (?, ?)")) {
                    for (UUID tagId : command.tagIds()) { statement.setObject(1, thread.id()); statement.setObject(2, tagId); statement.addBatch(); }
                    statement.executeBatch();
                }
                connection.commit(); return new ForumPost(thread, command.tagIds());
            } catch (SQLException | RuntimeException exception) { connection.rollback(); throw exception; }
        } catch (SQLException exception) { throw new IllegalStateException("failed to create forum post", exception); }
    }

    ThreadWriteReceipt write(ThreadWriteCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        ThreadChannel thread = thread(command.guildId(), command.threadId());
        if (thread.archived()) throw new IllegalStateException("archived thread cannot receive writes");
        String content = text(command.content(), "thread message content is required"); Instant now = clock.instant();
        try (Connection connection = dataSource.getConnection(); PreparedStatement message = connection.prepareStatement("INSERT INTO thread_messages (id, thread_id, author_id, content, created_at) VALUES (?, ?, ?, ?, ?)"); PreparedStatement update = connection.prepareStatement("UPDATE threads SET last_activity_at = ?, updated_at = ? WHERE id = ?")) {
            message.setObject(1, UUID.randomUUID()); message.setObject(2, thread.id()); message.setObject(3, command.authorId()); message.setString(4, content); message.setTimestamp(5, Timestamp.from(now)); message.executeUpdate();
            update.setTimestamp(1, Timestamp.from(now)); update.setTimestamp(2, Timestamp.from(now)); update.setObject(3, thread.id()); update.executeUpdate();
            return new ThreadWriteReceipt(thread.id(), command.authorId(), content, now);
        } catch (SQLException exception) { throw new IllegalStateException("failed to write thread message", exception); }
    }

    ThreadChannel archive(UUID guildId, UUID threadId) { return updateThread(thread(guildId, threadId), true, null); }
    ThreadChannel reopen(UUID guildId, UUID threadId) { Instant now = clock.instant(); return updateThread(thread(guildId, threadId), false, now); }
    ThreadChannel thread(UUID guildId, UUID threadId) {
        require(guildId, "guildId must not be null"); require(threadId, "threadId must not be null");
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM threads WHERE id = ? AND guild_id = ?")) {
            statement.setObject(1, threadId); statement.setObject(2, guildId); try (ResultSet result = statement.executeQuery()) { if (!result.next()) throw new ThreadNotFoundException(); return thread(result); }
        } catch (SQLException exception) { throw new IllegalStateException("failed to read thread", exception); }
    }

    int archiveExpired() {
        Instant now = clock.instant(); List<ThreadChannel> expired = new ArrayList<>();
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("SELECT * FROM threads WHERE archived = FALSE AND last_activity_at + (auto_archive_minutes * INTERVAL '1 minute') <= ?")) {
            statement.setTimestamp(1, Timestamp.from(now)); try (ResultSet result = statement.executeQuery()) { while (result.next()) expired.add(thread(result)); }
        } catch (SQLException exception) { throw new IllegalStateException("failed to find expired threads", exception); }
        expired.forEach(thread -> updateThread(thread, true, null)); return expired.size();
    }

    private ThreadChannel createThread(Connection connection, CreateThreadCommand command) throws SQLException {
        Instant now = clock.instant(); ThreadChannel thread = new ThreadChannel(UUID.randomUUID(), command.guildId(), command.parentChannelId(), command.ownerId(), text(command.name(), "thread name is required"), command.type(), false, command.autoArchiveMinutes(), now, now, now);
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO threads (id, guild_id, parent_channel_id, owner_id, name, type, archived, auto_archive_minutes, last_activity_at, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) { bindThread(statement, thread); statement.executeUpdate(); }
        return thread;
    }
    private ThreadChannel updateThread(ThreadChannel current, boolean archived, Instant activity) {
        Instant now = clock.instant(); Instant lastActivity = activity == null ? current.lastActivityAt() : activity;
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement("UPDATE threads SET archived = ?, last_activity_at = ?, updated_at = ? WHERE id = ?")) {
            statement.setBoolean(1, archived); statement.setTimestamp(2, Timestamp.from(lastActivity)); statement.setTimestamp(3, Timestamp.from(now)); statement.setObject(4, current.id()); statement.executeUpdate();
            return new ThreadChannel(current.id(), current.guildId(), current.parentChannelId(), current.ownerId(), current.name(), current.type(), archived, current.autoArchiveMinutes(), lastActivity, current.createdAt(), now);
        } catch (SQLException exception) { throw new IllegalStateException("failed to update thread", exception); }
    }
    private static boolean allowedTag(Connection connection, UUID tagId, UUID guildId, UUID channelId) throws SQLException { try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM forum_tags WHERE id = ? AND guild_id = ? AND forum_channel_id = ?")) { statement.setObject(1, tagId); statement.setObject(2, guildId); statement.setObject(3, channelId); try (ResultSet result = statement.executeQuery()) { return result.next(); } } }
    private static ThreadChannel thread(ResultSet result) throws SQLException { return new ThreadChannel(result.getObject("id", UUID.class), result.getObject("guild_id", UUID.class), result.getObject("parent_channel_id", UUID.class), result.getObject("owner_id", UUID.class), result.getString("name"), ThreadType.valueOf(result.getString("type")), result.getBoolean("archived"), result.getInt("auto_archive_minutes"), result.getTimestamp("last_activity_at").toInstant(), result.getTimestamp("created_at").toInstant(), result.getTimestamp("updated_at").toInstant()); }
    private static void bindThread(PreparedStatement statement, ThreadChannel thread) throws SQLException { statement.setObject(1, thread.id()); statement.setObject(2, thread.guildId()); statement.setObject(3, thread.parentChannelId()); statement.setObject(4, thread.ownerId()); statement.setString(5, thread.name()); statement.setString(6, thread.type().name()); statement.setBoolean(7, thread.archived()); statement.setInt(8, thread.autoArchiveMinutes()); statement.setTimestamp(9, Timestamp.from(thread.lastActivityAt())); statement.setTimestamp(10, Timestamp.from(thread.createdAt())); statement.setTimestamp(11, Timestamp.from(thread.updatedAt())); }
    private static void require(Object value, String message) { if (value == null) throw new NullPointerException(message); }
    private static String text(String value, String message) { if (value == null || value.isBlank()) throw new IllegalArgumentException(message); return value; }
}
