package com.example.discord.thread;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class InMemoryThreadService implements ThreadService {
    private final Clock clock;
    private final Map<UUID, ThreadChannel> threads = new LinkedHashMap<>();
    private final Map<UUID, ForumTag> forumTags = new LinkedHashMap<>();
    private final Map<UUID, ForumPost> forumPosts = new LinkedHashMap<>();

    public InMemoryThreadService() {
        this(Clock.systemUTC());
    }

    public InMemoryThreadService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized ThreadChannel createThread(CreateThreadCommand command) {
        requireCreateThreadCommand(command);
        Instant now = clock.instant();
        ThreadChannel thread = new ThreadChannel(
            UUID.randomUUID(),
            command.guildId(),
            command.parentChannelId(),
            command.ownerId(),
            requireText(command.name(), "thread name is required"),
            command.type(),
            false,
            requirePositive(command.autoArchiveMinutes(), "auto archive minutes must be positive"),
            now,
            now,
            now
        );
        threads.put(thread.id(), thread);
        return thread;
    }

    public synchronized ForumTag createForumTag(UUID guildId, UUID forumChannelId, String name) {
        requireIds(guildId, forumChannelId);
        ForumTag tag = new ForumTag(UUID.randomUUID(), guildId, forumChannelId, requireText(name, "forum tag name is required"));
        forumTags.put(tag.id(), tag);
        return tag;
    }

    public synchronized ForumPost createForumPost(CreateForumPostCommand command) {
        requireCreateForumPostCommand(command);
        if (command.tagIds().isEmpty()) {
            throw new IllegalArgumentException("forum post requires at least one allowed tag");
        }
        for (UUID tagId : command.tagIds()) {
            ForumTag tag = forumTags.get(tagId);
            if (tag == null || !tag.guildId().equals(command.guildId()) || !tag.forumChannelId().equals(command.forumChannelId())) {
                throw new IllegalArgumentException("forum post requires allowed tag");
            }
        }
        ThreadChannel thread = createThread(new CreateThreadCommand(
            command.guildId(),
            command.forumChannelId(),
            command.authorId(),
            command.title(),
            ThreadType.PUBLIC,
            command.autoArchiveMinutes()
        ));
        ForumPost post = new ForumPost(thread, command.tagIds());
        forumPosts.put(thread.id(), post);
        return post;
    }

    public synchronized ThreadWriteReceipt write(ThreadWriteCommand command) {
        requireWriteCommand(command);
        ThreadChannel current = requireThread(command.guildId(), command.threadId());
        if (current.archived()) {
            throw new IllegalStateException("archived thread cannot receive writes");
        }
        String content = requireText(command.content(), "thread message content is required");
        Instant now = clock.instant();
        threads.put(current.id(), copy(current, current.archived(), now, now));
        return new ThreadWriteReceipt(current.id(), command.authorId(), content, now);
    }

    public synchronized ThreadChannel archive(UUID guildId, UUID threadId) {
        ThreadChannel current = requireThread(guildId, threadId);
        ThreadChannel archived = copy(current, true, current.lastActivityAt(), clock.instant());
        threads.put(archived.id(), archived);
        return archived;
    }

    public synchronized ThreadChannel reopen(UUID guildId, UUID threadId) {
        ThreadChannel current = requireThread(guildId, threadId);
        Instant now = clock.instant();
        ThreadChannel reopened = copy(current, false, now, now);
        threads.put(reopened.id(), reopened);
        return reopened;
    }

    public synchronized ThreadChannel thread(UUID guildId, UUID threadId) {
        return requireThread(guildId, threadId);
    }

    public synchronized int archiveExpired() {
        Instant now = clock.instant();
        List<ThreadChannel> expired = threads.values().stream()
            .filter(thread -> !thread.archived())
            .filter(thread -> !thread.lastActivityAt().plusSeconds(thread.autoArchiveMinutes() * 60L).isAfter(now))
            .toList();
        for (ThreadChannel thread : expired) {
            threads.put(thread.id(), copy(thread, true, thread.lastActivityAt(), now));
        }
        return expired.size();
    }

    private ThreadChannel requireThread(UUID guildId, UUID threadId) {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(threadId, "threadId must not be null");
        ThreadChannel thread = threads.get(threadId);
        if (thread == null || !thread.guildId().equals(guildId)) {
            throw new ThreadNotFoundException();
        }
        return thread;
    }

    private static ThreadChannel copy(ThreadChannel current, boolean archived, Instant lastActivityAt, Instant updatedAt) {
        return new ThreadChannel(
            current.id(),
            current.guildId(),
            current.parentChannelId(),
            current.ownerId(),
            current.name(),
            current.type(),
            archived,
            current.autoArchiveMinutes(),
            lastActivityAt,
            current.createdAt(),
            updatedAt
        );
    }

    private static void requireCreateThreadCommand(CreateThreadCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireIds(command.guildId(), command.parentChannelId());
        Objects.requireNonNull(command.ownerId(), "ownerId must not be null");
        Objects.requireNonNull(command.type(), "thread type must not be null");
    }

    private static void requireCreateForumPostCommand(CreateForumPostCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireIds(command.guildId(), command.forumChannelId());
        Objects.requireNonNull(command.authorId(), "authorId must not be null");
        Objects.requireNonNull(command.tagIds(), "tagIds must not be null");
    }

    private static void requireWriteCommand(ThreadWriteCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(command.guildId(), "guildId must not be null");
        Objects.requireNonNull(command.threadId(), "threadId must not be null");
        Objects.requireNonNull(command.authorId(), "authorId must not be null");
    }

    private static void requireIds(UUID guildId, UUID channelId) {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static int requirePositive(int value, String message) {
        if (value < 1) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
