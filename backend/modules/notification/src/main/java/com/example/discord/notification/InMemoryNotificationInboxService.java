package com.example.discord.notification;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class InMemoryNotificationInboxService {
    private final Clock clock;
    private final Map<NotificationKey, NotificationItem> items = new LinkedHashMap<>();
    private final Map<UUID, NotificationPreferences> preferences = new LinkedHashMap<>();

    public InMemoryNotificationInboxService(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public synchronized void recordMention(MentionNotificationCommand command) {
        for (UUID userId : command.mentionedUserIds()) {
            if (userId.equals(command.authorId()) || !command.visibleRecipientIds().contains(userId)) {
                continue;
            }
            addItem(
                userId,
                command.guildId(),
                command.channelId(),
                command.messageId(),
                command.sequence(),
                NotificationKind.MENTION,
                command.summary()
            );
        }
    }

    public synchronized void recordDirectMessage(UUID userId, UUID channelId, UUID messageId, long sequence, String summary) {
        addItem(userId, null, channelId, messageId, sequence, NotificationKind.DM, summary);
    }

    public synchronized void recordServerNotification(
        UUID userId,
        UUID guildId,
        UUID channelId,
        UUID sourceId,
        long sequence,
        String summary
    ) {
        addItem(userId, guildId, channelId, sourceId, sequence, NotificationKind.SERVER, summary);
    }

    public synchronized void updatePreferences(UUID userId, NotificationPreferences nextPreferences) {
        Objects.requireNonNull(userId, "userId must not be null");
        preferences.put(userId, Objects.requireNonNull(nextPreferences, "nextPreferences must not be null"));
    }

    public synchronized List<NotificationItem> inbox(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return items.values().stream()
            .filter(item -> item.userId().equals(userId))
            .sorted(Comparator.comparingLong(NotificationItem::sequence).reversed()
                .thenComparing(NotificationItem::createdAt, Comparator.reverseOrder()))
            .toList();
    }

    public synchronized long unreadCount(UUID userId) {
        return inbox(userId).stream()
            .filter(item -> !item.read())
            .count();
    }

    public synchronized void markChannelRead(UUID userId, UUID channelId, long lastReadSequence) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        if (lastReadSequence < 0) {
            throw new IllegalArgumentException("lastReadSequence must not be negative");
        }
        List<NotificationKey> keysToUpdate = new ArrayList<>();
        for (Map.Entry<NotificationKey, NotificationItem> entry : items.entrySet()) {
            NotificationItem item = entry.getValue();
            if (item.userId().equals(userId)
                && item.channelId().equals(channelId)
                && item.sequence() <= lastReadSequence
                && !item.read()) {
                keysToUpdate.add(entry.getKey());
            }
        }
        for (NotificationKey key : keysToUpdate) {
            items.put(key, items.get(key).markRead());
        }
    }

    private void addItem(
        UUID userId,
        UUID guildId,
        UUID channelId,
        UUID sourceId,
        long sequence,
        NotificationKind kind,
        String summary
    ) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(sourceId, "sourceId must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(summary, "summary must not be null");
        if (!preferences.getOrDefault(userId, NotificationPreferences.defaults()).enabled(kind)) {
            return;
        }
        NotificationKey key = new NotificationKey(userId, sourceId, kind);
        items.putIfAbsent(key, new NotificationItem(
            UUID.randomUUID(),
            userId,
            guildId,
            channelId,
            sourceId,
            sequence,
            kind,
            summary,
            false,
            clock.instant()
        ));
    }

    private record NotificationKey(UUID userId, UUID sourceId, NotificationKind kind) {
    }
}
