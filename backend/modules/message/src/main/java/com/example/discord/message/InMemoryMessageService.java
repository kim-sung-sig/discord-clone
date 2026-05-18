package com.example.discord.message;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class InMemoryMessageService {
    private static final Pattern USER_ID_MENTION = Pattern.compile("<@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>");
    private static final Pattern USERNAME_MENTION = Pattern.compile("(?<![A-Za-z0-9_.<])@([A-Za-z0-9][A-Za-z0-9-]{0,31})");
    private static final Comparator<Message> NEWEST_FIRST = Comparator
        .comparing(Message::createdAt)
        .thenComparing(message -> message.id().toString())
        .reversed();

    private final Clock clock;
    private final Map<UUID, Message> messages = new LinkedHashMap<>();

    public InMemoryMessageService() {
        this(Clock.systemUTC());
    }

    public InMemoryMessageService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    protected synchronized void putMessage(Message message) {
        messages.put(message.id(), message);
    }

    public synchronized Message create(CreateMessageCommand command) {
        requireCommand(command);
        String content = requireContent(command.content());
        Instant now = clock.instant();
        Message message = new Message(
            UUID.randomUUID(),
            command.guildId(),
            command.channelId(),
            command.authorId(),
            content,
            mentions(content),
            false,
            false,
            List.of(),
            now,
            now
        );
        messages.put(message.id(), message);
        return message;
    }

    public synchronized MessagePage messages(UUID guildId, UUID channelId, String beforeCursor, int limit) {
        requireIds(guildId, channelId);
        int pageSize = pageSize(limit);
        Cursor before = beforeCursor == null || beforeCursor.isBlank() ? null : Cursor.decode(beforeCursor);
        List<Message> page = messages.values().stream()
            .filter(message -> message.guildId().equals(guildId))
            .filter(message -> message.channelId().equals(channelId))
            .filter(message -> before == null || before.isAfter(message))
            .sorted(NEWEST_FIRST)
            .limit(pageSize + 1L)
            .toList();

        boolean hasMore = page.size() > pageSize;
        List<Message> visiblePage = hasMore ? page.subList(0, pageSize) : page;
        String nextCursor = hasMore ? Cursor.from(visiblePage.getLast()).encode() : null;
        return new MessagePage(visiblePage, nextCursor);
    }

    public synchronized Message edit(EditMessageCommand command) {
        requireCommand(command);
        String content = requireContent(command.content());
        Message current = requireMessage(command.guildId(), command.channelId(), command.messageId());
        if (current.deleted()) {
            throw new IllegalStateException("deleted message cannot be edited");
        }
        List<MessageEdit> history = new ArrayList<>(current.editHistory());
        history.add(new MessageEdit(current.content(), clock.instant()));
        Message updated = new Message(
            current.id(),
            current.guildId(),
            current.channelId(),
            current.authorId(),
            content,
            mentions(content),
            current.pinned(),
            false,
            history,
            current.createdAt(),
            clock.instant()
        );
        messages.put(updated.id(), updated);
        return updated;
    }

    public synchronized Message delete(UUID guildId, UUID channelId, UUID messageId) {
        Message current = requireMessage(guildId, channelId, messageId);
        Message deleted = new Message(
            current.id(),
            current.guildId(),
            current.channelId(),
            current.authorId(),
            "",
            List.of(),
            current.pinned(),
            true,
            List.of(),
            current.createdAt(),
            clock.instant()
        );
        messages.put(deleted.id(), deleted);
        return deleted;
    }

    public synchronized Message pin(UUID guildId, UUID channelId, UUID messageId) {
        return pinned(guildId, channelId, messageId, true);
    }

    public synchronized Message unpin(UUID guildId, UUID channelId, UUID messageId) {
        return pinned(guildId, channelId, messageId, false);
    }

    public synchronized List<Message> search(UUID guildId, UUID channelId, String query, int limit) {
        requireIds(guildId, channelId);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return List.of();
        }
        return messages.values().stream()
            .filter(message -> message.guildId().equals(guildId))
            .filter(message -> message.channelId().equals(channelId))
            .filter(message -> !message.deleted())
            .filter(message -> message.content().toLowerCase(Locale.ROOT).contains(normalized))
            .sorted(NEWEST_FIRST)
            .limit(pageSize(limit))
            .toList();
    }

    public synchronized List<Message> search(UUID guildId, Set<UUID> allowedChannelIds, String query, int limit) {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Set<UUID> channels = allowedChannelIds == null ? Set.of() : Set.copyOf(allowedChannelIds);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (channels.isEmpty() || normalized.isEmpty()) {
            return List.of();
        }
        return messages.values().stream()
            .filter(message -> message.guildId().equals(guildId))
            .filter(message -> channels.contains(message.channelId()))
            .filter(message -> !message.deleted())
            .filter(message -> message.content().toLowerCase(Locale.ROOT).contains(normalized))
            .sorted(NEWEST_FIRST)
            .limit(pageSize(limit))
            .toList();
    }

    public synchronized Message message(UUID guildId, UUID channelId, UUID messageId) {
        return requireMessage(guildId, channelId, messageId);
    }

    private Message pinned(UUID guildId, UUID channelId, UUID messageId, boolean pinned) {
        Message current = requireMessage(guildId, channelId, messageId);
        Message updated = new Message(
            current.id(),
            current.guildId(),
            current.channelId(),
            current.authorId(),
            current.content(),
            current.mentions(),
            pinned,
            current.deleted(),
            current.editHistory(),
            current.createdAt(),
            clock.instant()
        );
        messages.put(updated.id(), updated);
        return updated;
    }

    private Message requireMessage(UUID guildId, UUID channelId, UUID messageId) {
        requireIds(guildId, channelId);
        Objects.requireNonNull(messageId, "messageId must not be null");
        Message message = messages.get(messageId);
        if (message == null || !message.guildId().equals(guildId) || !message.channelId().equals(channelId)) {
            throw new MessageNotFoundException();
        }
        return message;
    }

    private static void requireCommand(CreateMessageCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireIds(command.guildId(), command.channelId());
        Objects.requireNonNull(command.authorId(), "authorId must not be null");
    }

    private static void requireCommand(EditMessageCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        requireIds(command.guildId(), command.channelId());
        Objects.requireNonNull(command.messageId(), "messageId must not be null");
    }

    private static void requireIds(UUID guildId, UUID channelId) {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
    }

    private static String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("message content is required");
        }
        return content;
    }

    private static int pageSize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
    }

    private static List<String> mentions(String content) {
        LinkedHashSet<String> mentions = new LinkedHashSet<>();
        var userIdMatcher = USER_ID_MENTION.matcher(content);
        while (userIdMatcher.find()) {
            mentions.add(UUID.fromString(userIdMatcher.group(1)).toString());
        }

        var usernameMatcher = USERNAME_MENTION.matcher(content);
        while (usernameMatcher.find()) {
            mentions.add(usernameMatcher.group(1).toLowerCase(Locale.ROOT));
        }
        return List.copyOf(mentions);
    }

    private record Cursor(Instant createdAt, String id) {
        static Cursor from(Message message) {
            return new Cursor(message.createdAt(), message.id().toString());
        }

        static Cursor decode(String encoded) {
            String decoded = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor");
            }
            return new Cursor(Instant.parse(parts[0]), parts[1]);
        }

        String encode() {
            String value = createdAt + "|" + id;
            return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        boolean isAfter(Message message) {
            int createdAtComparison = createdAt.compareTo(message.createdAt());
            if (createdAtComparison != 0) {
                return createdAtComparison > 0;
            }
            return id.compareTo(message.id().toString()) > 0;
        }
    }
}
