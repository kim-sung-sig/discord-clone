package com.example.discord.message;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class InMemoryMessagePublicationInbox implements MessagePublicationInbox {
    private final Set<UUID> eventIds = new HashSet<>();

    @Override
    public synchronized boolean isProcessed(UUID eventId) {
        return eventIds.contains(eventId);
    }

    @Override
    public synchronized void markProcessed(UUID eventId) {
        eventIds.add(eventId);
    }
}
