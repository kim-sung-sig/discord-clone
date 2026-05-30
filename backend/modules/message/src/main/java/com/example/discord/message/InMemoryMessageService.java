package com.example.discord.message;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

public class InMemoryMessageService {
    private static final Pattern USER_ID_MENTION = Pattern.compile("<@([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})>");
    private static final Pattern USERNAME_MENTION = Pattern.compile("(?<![A-Za-z0-9_.<])@([A-Za-z0-9][A-Za-z0-9-]{0,31})");
    private static final Comparator<MessageOrder> NEWEST_MESSAGE_ORDER = Comparator
        .comparing(MessageOrder::createdAt)
        .thenComparing(order -> order.messageId().toString())
        .reversed();

    private final Clock clock;
    private final Map<UUID, Message> messages = new LinkedHashMap<>();
    private final Map<ChannelKey, NavigableSet<MessageOrder>> messagesByChannel = new LinkedHashMap<>();

    public InMemoryMessageService() {
        this(Clock.systemUTC());
    }

    public InMemoryMessageService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    protected synchronized void putMessage(Message message) {
        upsertMessage(message);
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
        upsertMessage(message);
        return message;
    }

    public synchronized MessagePage messages(UUID guildId, UUID channelId, String beforeCursor, int limit) {
        requireIds(guildId, channelId);
        int pageSize = pageSize(limit);
        Cursor before = beforeCursor == null || beforeCursor.isBlank() ? null : Cursor.decode(beforeCursor);
        List<Message> page = new ArrayList<>(pageSize + 1);
        for (MessageOrder order : orderedMessages(guildId, channelId)) {
            Message message = messages.get(order.messageId());
            if (message != null && (before == null || before.isAfter(message))) {
                page.add(message);
                if (page.size() > pageSize) {
                    break;
                }
            }
        }

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
        upsertMessage(updated);
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
        upsertMessage(deleted);
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
        return searchChannel(guildId, channelId, normalized, pageSize(limit));
    }

    public synchronized List<Message> search(UUID guildId, Set<UUID> allowedChannelIds, String query, int limit) {
        Objects.requireNonNull(guildId, "guildId must not be null");
        Set<UUID> channels = allowedChannelIds == null ? Set.of() : Set.copyOf(allowedChannelIds);
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (channels.isEmpty() || normalized.isEmpty()) {
            return List.of();
        }
        return searchChannels(guildId, channels, normalized, pageSize(limit));
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
        upsertMessage(updated);
        return updated;
    }

    private void upsertMessage(Message message) {
        Objects.requireNonNull(message, "message must not be null");
        Message previous = messages.put(message.id(), message);
        if (previous != null) {
            removeFromChannelIndex(previous);
        }
        messagesByChannel
            .computeIfAbsent(ChannelKey.from(message), key -> new TreeSet<>(NEWEST_MESSAGE_ORDER))
            .add(MessageOrder.from(message));
    }

    private void removeFromChannelIndex(Message message) {
        ChannelKey key = ChannelKey.from(message);
        NavigableSet<MessageOrder> orders = messagesByChannel.get(key);
        if (orders == null) {
            return;
        }
        orders.remove(MessageOrder.from(message));
        if (orders.isEmpty()) {
            messagesByChannel.remove(key);
        }
    }

    private NavigableSet<MessageOrder> orderedMessages(UUID guildId, UUID channelId) {
        NavigableSet<MessageOrder> orders = messagesByChannel.get(new ChannelKey(guildId, channelId));
        if (orders == null) {
            return new TreeSet<>(NEWEST_MESSAGE_ORDER);
        }
        return orders;
    }

    private List<Message> searchChannel(UUID guildId, UUID channelId, String normalized, int limit) {
        List<Message> results = new ArrayList<>(limit);
        for (MessageOrder order : orderedMessages(guildId, channelId)) {
            Message message = messages.get(order.messageId());
            if (matchesSearch(message, normalized)) {
                results.add(message);
                if (results.size() == limit) {
                    break;
                }
            }
        }
        return List.copyOf(results);
    }

    private List<Message> searchChannels(UUID guildId, Set<UUID> channelIds, String normalized, int limit) {
        PriorityQueue<ChannelSearchCursor> candidates = new PriorityQueue<>(
            (left, right) -> NEWEST_MESSAGE_ORDER.compare(left.order(), right.order())
        );
        for (UUID channelId : channelIds) {
            Iterator<MessageOrder> iterator = orderedMessages(guildId, channelId).iterator();
            if (iterator.hasNext()) {
                candidates.add(new ChannelSearchCursor(iterator.next(), iterator));
            }
        }

        List<Message> results = new ArrayList<>(limit);
        while (!candidates.isEmpty() && results.size() < limit) {
            ChannelSearchCursor cursor = candidates.poll();
            Message message = messages.get(cursor.order().messageId());
            if (matchesSearch(message, normalized)) {
                results.add(message);
            }
            if (cursor.iterator().hasNext()) {
                candidates.add(new ChannelSearchCursor(cursor.iterator().next(), cursor.iterator()));
            }
        }
        return List.copyOf(results);
    }

    private static boolean matchesSearch(Message message, String normalized) {
        return message != null
            && !message.deleted()
            && message.content().toLowerCase(Locale.ROOT).contains(normalized);
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

    private record ChannelKey(UUID guildId, UUID channelId) {
        static ChannelKey from(Message message) {
            return new ChannelKey(message.guildId(), message.channelId());
        }
    }

    private record MessageOrder(Instant createdAt, UUID messageId) {
        static MessageOrder from(Message message) {
            return new MessageOrder(message.createdAt(), message.id());
        }
    }

    private record ChannelSearchCursor(MessageOrder order, Iterator<MessageOrder> iterator) {
    }
}
