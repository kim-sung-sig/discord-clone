package com.example.discord.presence;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class InMemoryPresenceService {
    private final PresenceTtlStore ttlStore;
    private final Clock clock;
    private final Map<ReadKey, ReadMarker> readMarkers = new HashMap<>();

    public InMemoryPresenceService(PresenceTtlStore ttlStore, Clock clock) {
        this.ttlStore = Objects.requireNonNull(ttlStore, "ttlStore must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public void updatePresence(UUID userId, PresenceStatus status, Duration ttl) {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        if (status == PresenceStatus.OFFLINE) {
            ttlStore.remove(presenceKey(userId));
            return;
        }
        ttlStore.put(presenceKey(userId), new UserPresence(userId, status, clock.instant()), ttl);
    }

    public UserPresence presence(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return ttlStore.get(presenceKey(userId))
            .map(UserPresence.class::cast)
            .orElseGet(() -> new UserPresence(userId, PresenceStatus.OFFLINE, clock.instant()));
    }

    public void startTyping(UUID channelId, UUID userId, Duration ttl) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        ttlStore.put(typingKey(channelId, userId), new TypingIndicator(channelId, userId, clock.instant()), ttl);
    }

    public List<UUID> typingUsers(UUID channelId) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        return ttlStore.keys(typingPrefix(channelId)).stream()
            .map(ttlStore::get)
            .flatMap(Optional::stream)
            .map(TypingIndicator.class::cast)
            .map(TypingIndicator::userId)
            .toList();
    }

    public synchronized ReadMarker markRead(UUID channelId, UUID userId, long lastReadSequence) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        if (lastReadSequence < 0) {
            throw new IllegalArgumentException("lastReadSequence must not be negative");
        }
        ReadKey key = new ReadKey(channelId, userId);
        long current = readMarkers.getOrDefault(key, new ReadMarker(channelId, userId, 0L, clock.instant())).lastReadSequence();
        ReadMarker marker = new ReadMarker(channelId, userId, Math.max(current, lastReadSequence), clock.instant());
        readMarkers.put(key, marker);
        return marker;
    }

    public synchronized ReadMarker readMarker(UUID channelId, UUID userId) {
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        return readMarkers.getOrDefault(new ReadKey(channelId, userId), new ReadMarker(channelId, userId, 0L, clock.instant()));
    }

    public synchronized long unreadCount(UUID channelId, UUID userId, long lastMessageSequence, List<Long> authoredSequences) {
        Objects.requireNonNull(authoredSequences, "authoredSequences must not be null");
        long lastReadSequence = readMarker(channelId, userId).lastReadSequence();
        if (lastMessageSequence <= lastReadSequence) {
            return 0L;
        }
        Set<Long> authoredUnread = new HashSet<>();
        for (Long sequence : authoredSequences) {
            if (sequence != null && sequence > lastReadSequence && sequence <= lastMessageSequence) {
                authoredUnread.add(sequence);
            }
        }
        return (lastMessageSequence - lastReadSequence) - authoredUnread.size();
    }

    private static String presenceKey(UUID userId) {
        return "presence:user:" + userId;
    }

    private static String typingPrefix(UUID channelId) {
        return "typing:channel:" + channelId + ":";
    }

    private static String typingKey(UUID channelId, UUID userId) {
        return typingPrefix(channelId) + userId;
    }

    private record ReadKey(UUID channelId, UUID userId) {
    }
}
