package com.example.discord.voice;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryVoiceService {
    private static final long TOKEN_TTL_SECONDS = 900L;
    static final int MAX_RETAINED_EVENTS = 1_000;

    private final Clock clock;
    private final VoiceTokenSigner tokenSigner;
    private final Map<VoiceKey, VoiceParticipantState> participants = new LinkedHashMap<>();
    private final List<VoiceStateEvent> events = new ArrayList<>();

    public InMemoryVoiceService(Clock clock, VoiceTokenSigner tokenSigner) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.tokenSigner = Objects.requireNonNull(tokenSigner, "tokenSigner must not be null");
    }

    public synchronized VoiceJoinResult join(UUID guildId, UUID channelId, UUID userId) {
        Instant now = clock.instant();
        VoiceParticipantState participant = new VoiceParticipantState(
            guildId,
            channelId,
            userId,
            false,
            false,
            false,
            false,
            now
        );
        participants.put(new VoiceKey(guildId, channelId, userId), participant);
        appendEvent(VoiceStateEvent.from(VoiceStateEvent.JOINED, participant, now));
        return new VoiceJoinResult(participant, tokenSigner.sign(new VoiceTokenSigningRequest(
            guildId,
            channelId,
            userId,
            now,
            TOKEN_TTL_SECONDS
        )));
    }

    public synchronized Optional<VoiceParticipantState> leave(UUID guildId, UUID channelId, UUID userId) {
        VoiceParticipantState removed = participants.remove(new VoiceKey(guildId, channelId, userId));
        if (removed != null) {
            Instant now = clock.instant();
            VoiceParticipantState left = removed.withState(false, false, false, false, now);
            appendEvent(VoiceStateEvent.from(VoiceStateEvent.LEFT, left, now));
            return Optional.of(left);
        }
        return Optional.empty();
    }

    public synchronized VoiceParticipantState update(VoiceStateUpdateCommand command) {
        VoiceKey key = new VoiceKey(command.guildId(), command.channelId(), command.userId());
        VoiceParticipantState current = participants.get(key);
        if (current == null) {
            throw new IllegalArgumentException("participant is not joined");
        }
        Instant now = clock.instant();
        VoiceParticipantState updated = current.withState(
            command.muted(),
            command.deafened(),
            command.speaking(),
            command.screenSharing(),
            now
        );
        participants.put(key, updated);
        appendEvent(VoiceStateEvent.from(VoiceStateEvent.UPDATED, updated, now));
        return updated;
    }

    public synchronized List<VoiceParticipantState> participants(UUID guildId, UUID channelId) {
        return participants.values().stream()
            .filter(participant -> participant.guildId().equals(guildId) && participant.channelId().equals(channelId))
            .sorted(Comparator.comparing(VoiceParticipantState::userId))
            .toList();
    }

    public synchronized List<VoiceStateEvent> events() {
        return List.copyOf(events);
    }

    private void appendEvent(VoiceStateEvent event) {
        events.add(event);
        while (events.size() > MAX_RETAINED_EVENTS) {
            events.remove(0);
        }
    }

    private record VoiceKey(UUID guildId, UUID channelId, UUID userId) {
        private VoiceKey {
            if (guildId == null) {
                throw new IllegalArgumentException("guildId is required");
            }
            if (channelId == null) {
                throw new IllegalArgumentException("channelId is required");
            }
            if (userId == null) {
                throw new IllegalArgumentException("userId is required");
            }
        }
    }
}
