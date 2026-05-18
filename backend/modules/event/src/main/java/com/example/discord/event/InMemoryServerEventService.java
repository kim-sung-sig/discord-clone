package com.example.discord.event;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InMemoryServerEventService {
    private final Map<UUID, ServerEvent> events = new LinkedHashMap<>();
    private final List<ServerEventSignal> signals = new ArrayList<>();
    private Instant lastSignalCreatedAt = Instant.EPOCH;

    public synchronized ServerEvent createEvent(CreateServerEventCommand command, boolean canManageEvents) {
        if (!canManageEvents) {
            throw new SecurityException("server event management permission is required");
        }
        ServerEvent event = new ServerEvent(
            UUID.randomUUID(),
            command.guildId(),
            command.channelId(),
            command.creatorId(),
            command.title(),
            command.startsAt(),
            command.endsAt(),
            ServerEventStatus.SCHEDULED,
            List.of(),
            Instant.now()
        );
        events.put(event.id(), event);
        appendSignal(event.guildId(), event.id(), event.creatorId(), ServerEventSignalType.EVENT_CREATED, "event created");
        return event;
    }

    public synchronized ServerEvent rsvpInterested(UUID guildId, UUID eventId, UUID memberId) {
        ServerEvent current = requireEvent(guildId, eventId);
        LinkedHashSet<UUID> interested = new LinkedHashSet<>(current.interestedMemberIds());
        boolean changed = interested.add(memberId);
        ServerEvent updated = replace(current, current.status(), List.copyOf(interested));
        if (changed) {
            appendSignal(guildId, eventId, memberId, ServerEventSignalType.EVENT_RSVP_UPDATED, "event rsvp updated");
        }
        return updated;
    }

    public synchronized ServerEvent cancelEvent(UUID guildId, UUID eventId, UUID actorId, String reason) {
        ServerEvent current = requireEvent(guildId, eventId);
        ServerEvent updated = replace(current, ServerEventStatus.CANCELED, current.interestedMemberIds());
        appendSignal(guildId, eventId, actorId, ServerEventSignalType.EVENT_CANCELED, reason);
        return updated;
    }

    public synchronized List<ServerEvent> visibleEvents(UUID guildId, Set<UUID> visibleChannelIds) {
        Set<UUID> visible = visibleChannelIds == null ? Set.of() : Set.copyOf(visibleChannelIds);
        return events.values().stream()
            .filter(event -> event.guildId().equals(guildId))
            .filter(event -> visible.contains(event.channelId()))
            .sorted(Comparator.comparing(ServerEvent::startsAt))
            .toList();
    }

    public synchronized List<ServerEventSignal> signals() {
        return signals.stream()
            .sorted(Comparator.comparing(ServerEventSignal::createdAt).reversed())
            .toList();
    }

    private ServerEvent replace(ServerEvent current, ServerEventStatus status, List<UUID> interestedMemberIds) {
        ServerEvent updated = new ServerEvent(
            current.id(),
            current.guildId(),
            current.channelId(),
            current.creatorId(),
            current.title(),
            current.startsAt(),
            current.endsAt(),
            status,
            interestedMemberIds,
            Instant.now()
        );
        events.put(updated.id(), updated);
        return updated;
    }

    private ServerEvent requireEvent(UUID guildId, UUID eventId) {
        ServerEvent event = events.get(eventId);
        if (event == null || !event.guildId().equals(guildId)) {
            throw new IllegalArgumentException("event not found");
        }
        return event;
    }

    private void appendSignal(UUID guildId, UUID eventId, UUID actorId, ServerEventSignalType type, String detail) {
        signals.add(new ServerEventSignal(UUID.randomUUID(), guildId, eventId, actorId, type, detail, nextSignalInstant()));
    }

    private Instant nextSignalInstant() {
        Instant now = Instant.now();
        if (!now.isAfter(lastSignalCreatedAt)) {
            now = lastSignalCreatedAt.plusNanos(1);
        }
        lastSignalCreatedAt = now;
        return now;
    }
}
