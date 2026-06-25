package com.example.discord.message;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

public class InMemoryMessageService
    implements
    MessageStore,
    MessagePublicationStore,
    MessagePublicationOutbox,
    MessagePublicationOutboxQueue,
    MessagePublicationDeadLetterQueue,
    ChannelMessagePagePort,
    ChannelMessageSearchPort,
    MessageLookupPort {
    private static final Comparator<MessageOrder> NEWEST_MESSAGE_ORDER = Comparator
        .comparing(MessageOrder::createdAt)
        .thenComparing(order -> order.messageId().toString())
        .reversed();

    private final Clock clock;
    private final Map<UUID, Message> messages = new LinkedHashMap<>();
    private final Map<ChannelKey, NavigableSet<MessageOrder>> messagesByChannel = new LinkedHashMap<>();
    private final Map<IdempotencyScope, UUID> messageIdsByIdempotency = new LinkedHashMap<>();
    private final Map<UUID, MessagePublished> pendingPublications = new LinkedHashMap<>();
    private final Map<UUID, UUID> publicationClaimTokens = new LinkedHashMap<>();
    private final Map<UUID, Integer> publicationAttempts = new LinkedHashMap<>();
    private final Map<UUID, String> publicationLastErrors = new LinkedHashMap<>();
    private final Map<UUID, Instant> publicationRetryAfter = new LinkedHashMap<>();
    private final Map<UUID, DeadLetteredMessagePublication> deadLetterPublications = new LinkedHashMap<>();

    public InMemoryMessageService() {
        this(Clock.systemUTC());
    }

    public InMemoryMessageService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    protected synchronized void putMessage(Message message) {
        upsertMessage(message);
    }

    @Override
    public synchronized java.util.Optional<Message> findById(UUID messageId) {
        Objects.requireNonNull(messageId, "messageId must not be null");
        return java.util.Optional.ofNullable(messages.get(messageId));
    }

    @Override
    public synchronized java.util.Optional<Message> findByIdempotencyKey(
        MessageAuthor author,
        MessageTarget target,
        IdempotencyKey idempotencyKey
    ) {
        Objects.requireNonNull(author, "author must not be null");
        Objects.requireNonNull(target, "target must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        UUID messageId = messageIdsByIdempotency.get(new IdempotencyScope(author, target, idempotencyKey));
        return java.util.Optional.ofNullable(messageId).map(messages::get);
    }

    @Override
    public synchronized Message save(Message message, IdempotencyKey idempotencyKey) {
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        save(message);
        messageIdsByIdempotency.put(new IdempotencyScope(message.author(), message.target(), idempotencyKey), message.id());
        return message;
    }

    @Override
    public synchronized Message savePublished(
        Message message,
        IdempotencyKey idempotencyKey,
        MessagePublished event
    ) {
        Objects.requireNonNull(event, "event must not be null");
        Message saved = save(message, idempotencyKey);
        pendingPublications.put(event.eventId(), event);
        return saved;
    }

    @Override
    public synchronized void append(MessagePublished event) {
        Objects.requireNonNull(event, "event must not be null");
        pendingPublications.put(event.eventId(), event);
    }

    @Override
    public synchronized List<ClaimedMessagePublication> claimPendingPublications(
        int limit,
        Instant claimedAt,
        Duration lease
    ) {
        Objects.requireNonNull(claimedAt, "claimedAt must not be null");
        Objects.requireNonNull(lease, "lease must not be null");
        return pendingPublications.values().stream()
            .filter(event -> !publicationClaimTokens.containsKey(event.eventId()))
            .filter(event -> eligibleForRetry(event, claimedAt))
            .limit(pageSize(limit))
            .map(event -> {
                UUID claimToken = UUID.randomUUID();
                publicationClaimTokens.put(event.eventId(), claimToken);
                return new ClaimedMessagePublication(event, claimToken);
            })
            .toList();
    }

    @Override
    public synchronized void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        if (!claimToken.equals(publicationClaimTokens.get(eventId))) {
            return;
        }
        pendingPublications.remove(eventId);
        publicationClaimTokens.remove(eventId);
        publicationRetryAfter.remove(eventId);
    }

    @Override
    public synchronized void releaseFailed(
        UUID eventId,
        UUID claimToken,
        String errorMessage,
        Instant failedAt,
        Duration retryDelay
    ) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(claimToken, "claimToken must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        Objects.requireNonNull(retryDelay, "retryDelay must not be null");
        if (claimToken.equals(publicationClaimTokens.get(eventId))) {
            publicationClaimTokens.remove(eventId);
            int attempts = publicationAttempts.merge(eventId, 1, Integer::sum);
            String lastError = errorMessage == null ? "" : errorMessage;
            publicationLastErrors.put(eventId, lastError);
            if (attempts >= 10) {
                MessagePublished event = pendingPublications.remove(eventId);
                publicationRetryAfter.remove(eventId);
                if (event != null) {
                    deadLetterPublications.put(
                        eventId,
                        new DeadLetteredMessagePublication(event, attempts, lastError, failedAt)
                    );
                }
            } else {
                publicationRetryAfter.put(eventId, failedAt.plus(retryDelay));
            }
        }
    }

    @Override
    public synchronized List<DeadLetteredMessagePublication> listDeadLetters(int limit) {
        return deadLetterPublications.values().stream()
            .limit(pageSize(limit))
            .toList();
    }

    @Override
    public synchronized boolean requeueDeadLetter(UUID eventId, Instant requestedAt) {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(requestedAt, "requestedAt must not be null");
        DeadLetteredMessagePublication deadLetter = deadLetterPublications.remove(eventId);
        if (deadLetter == null) {
            return false;
        }
        publicationAttempts.remove(eventId);
        publicationLastErrors.remove(eventId);
        publicationClaimTokens.remove(eventId);
        publicationRetryAfter.remove(eventId);
        pendingPublications.put(eventId, deadLetter.event());
        return true;
    }

    private boolean eligibleForRetry(MessagePublished event, Instant claimedAt) {
        Instant retryAfter = publicationRetryAfter.get(event.eventId());
        return retryAfter == null || !retryAfter.isAfter(claimedAt);
    }

    @Override
    public synchronized Message save(Message message) {
        upsertMessage(message);
        return message;
    }

    public synchronized Message create(CreateMessageCommand command) {
        requireCommand(command);
        MessageContent content = requireContent(command.content());
        Instant now = clock.instant();
        Message message = new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(command.authorId()),
            new ChannelMessageTarget(command.guildId(), command.channelId()),
            content,
            List.of(),
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

    @Override
    public synchronized MessagePage read(ChannelMessageTarget target, String beforeCursor, int limit) {
        Objects.requireNonNull(target, "target must not be null");
        return messages(target.guildId(), target.channelId(), beforeCursor, limit);
    }

    public synchronized Message edit(EditMessageCommand command) {
        requireCommand(command);
        MessageContent content = requireContent(command.content());
        Message current = requireMessage(command.guildId(), command.channelId(), command.messageId());
        if (current.deleted()) {
            throw new IllegalStateException("deleted message cannot be edited");
        }
        List<MessageEdit> history = new ArrayList<>(current.editHistory());
        history.add(new MessageEdit(current.content(), clock.instant()));
        Message updated = new Message(
            current.id(),
            current.author(),
            current.target(),
            content,
            List.of(),
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
            current.author(),
            current.target(),
            new MessageContent("[deleted]"),
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

    @Override
    public synchronized List<Message> search(ChannelMessageTarget target, String query, int limit) {
        Objects.requireNonNull(target, "target must not be null");
        return search(target.guildId(), target.channelId(), query, limit);
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

    @Override
    public synchronized Message requireMessage(ChannelMessageTarget target, UUID messageId) {
        Objects.requireNonNull(target, "target must not be null");
        return message(target.guildId(), target.channelId(), messageId);
    }

    private Message pinned(UUID guildId, UUID channelId, UUID messageId, boolean pinned) {
        Message current = requireMessage(guildId, channelId, messageId);
        Message updated = new Message(
            current.id(),
            current.author(),
            current.target(),
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
            && message.content().value().toLowerCase(Locale.ROOT).contains(normalized);
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

    private static MessageContent requireContent(String content) {
        try {
            return new MessageContent(content);
        } catch (NullPointerException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("message content is required", exception);
        }
    }

    private static int pageSize(int limit) {
        if (limit < 1) {
            return 50;
        }
        return Math.min(limit, 100);
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

    private record IdempotencyScope(MessageAuthor author, MessageTarget target, IdempotencyKey idempotencyKey) {
    }
}
