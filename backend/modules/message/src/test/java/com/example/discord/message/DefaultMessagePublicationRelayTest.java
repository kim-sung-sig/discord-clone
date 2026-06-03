package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultMessagePublicationRelayTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-03T13:00:00Z"), ZoneOffset.UTC);

    @Test
    void dispatchesBoundedPendingEventsAndMarksPublishedAfterSuccess() {
        MessagePublished first = event();
        MessagePublished second = event();
        RecordingOutbox outbox = new RecordingOutbox(List.of(first, second));
        List<MessagePublished> dispatched = new ArrayList<>();
        MessagePublicationRelay relay = new DefaultMessagePublicationRelay(outbox, dispatched::add, FIXED_CLOCK);

        int delivered = relay.relay(1);

        assertThat(delivered).isEqualTo(1);
        assertThat(dispatched).containsExactly(first);
        assertThat(outbox.requestedLimits).containsExactly(1);
        assertThat(outbox.requestedLeases).containsExactly(Duration.ofSeconds(30));
        assertThat(outbox.marked).containsExactly(first.eventId());
        assertThat(outbox.markTokens).containsExactly(outbox.claimToken);
        assertThat(outbox.publishedAt).containsExactly(FIXED_CLOCK.instant());
    }

    @Test
    void doesNotMarkFailedDispatchAsPublished() {
        MessagePublished event = event();
        RecordingOutbox outbox = new RecordingOutbox(List.of(event));
        RuntimeException failure = new RuntimeException("gateway unavailable");
        MessagePublicationRelay relay = new DefaultMessagePublicationRelay(
            outbox,
            ignored -> {
                throw failure;
            },
            FIXED_CLOCK
        );

        assertThatThrownBy(() -> relay.relay(10)).isSameAs(failure);
        assertThat(outbox.marked).isEmpty();
        assertThat(outbox.released).containsExactly(event.eventId());
        assertThat(outbox.releaseErrors).containsExactly("gateway unavailable");
        assertThat(outbox.releaseRetryDelays).containsExactly(Duration.ofSeconds(5));
    }

    private static MessagePublished event() {
        return new MessagePublished(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            List.of(),
            "correlation-" + UUID.randomUUID(),
            FIXED_CLOCK.instant()
        );
    }

    private static final class RecordingOutbox implements MessagePublicationOutboxQueue {
        private final List<MessagePublished> events;
        private final UUID claimToken = UUID.randomUUID();
        private final List<Integer> requestedLimits = new ArrayList<>();
        private final List<Duration> requestedLeases = new ArrayList<>();
        private final List<UUID> marked = new ArrayList<>();
        private final List<UUID> markTokens = new ArrayList<>();
        private final List<Instant> publishedAt = new ArrayList<>();
        private final List<UUID> released = new ArrayList<>();
        private final List<String> releaseErrors = new ArrayList<>();
        private final List<Duration> releaseRetryDelays = new ArrayList<>();

        private RecordingOutbox(List<MessagePublished> events) {
            this.events = events;
        }

        @Override
        public List<ClaimedMessagePublication> claimPendingPublications(
            int limit,
            Instant claimedAt,
            Duration lease
        ) {
            requestedLimits.add(limit);
            requestedLeases.add(lease);
            return events.stream()
                .limit(limit)
                .map(event -> new ClaimedMessagePublication(event, claimToken))
                .toList();
        }

        @Override
        public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
            marked.add(eventId);
            markTokens.add(claimToken);
            this.publishedAt.add(publishedAt);
        }

        @Override
        public void releaseFailed(
            UUID eventId,
            UUID claimToken,
            String errorMessage,
            Instant failedAt,
            Duration retryDelay
        ) {
            released.add(eventId);
            releaseErrors.add(errorMessage);
            releaseRetryDelays.add(retryDelay);
        }
    }
}
