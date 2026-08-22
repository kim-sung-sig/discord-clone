package com.example.discord.message;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class InMemoryMessagePublicationInbox implements MessagePublicationInbox {
    private final Set<UUID> eventIds = new HashSet<>();

    @Override
    public synchronized boolean claim(UUID eventId) {
        return eventIds.add(eventId);
    }

    @Override
    public synchronized void release(UUID eventId) {
        eventIds.remove(eventId);
    }
}
