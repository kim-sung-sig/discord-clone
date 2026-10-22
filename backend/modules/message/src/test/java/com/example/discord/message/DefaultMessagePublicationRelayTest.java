package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Test
    void redispatchesSameEventAfterAcknowledgedDispatchCannotBeMarkedPublished() {
        MessagePublished event = event();
        RecordingOutbox outbox = new RecordingOutbox(List.of(event));
        RuntimeException markFailure = new IllegalStateException("database disconnected after broker ack");
        outbox.markFailure = markFailure;
        List<MessagePublished> dispatched = new ArrayList<>();
        MessagePublicationRelay relay = new DefaultMessagePublicationRelay(outbox, dispatched::add, FIXED_CLOCK);

        assertThatThrownBy(() -> relay.relay(1)).isSameAs(markFailure);
        assertThat(outbox.marked).isEmpty();
        assertThat(outbox.released).containsExactly(event.eventId());
        assertThat(relay.relay(1)).isZero();
        outbox.markFailure = null;
        MessagePublicationRelay recovered = new DefaultMessagePublicationRelay(
            outbox, dispatched::add, Clock.offset(FIXED_CLOCK, Duration.ofSeconds(5)));

        assertThat(recovered.relay(1)).isEqualTo(1);
        assertThat(dispatched).containsExactly(event, event);
        assertThat(outbox.marked).containsExactly(event.eventId());
        assertThat(outbox.unpublishedBacklogCount()).isZero();
    }

    @Test
    void preservesMarkFailureWhenReleasingClaimAlsoFails() {
        MessagePublished event = event();
        RecordingOutbox outbox = new RecordingOutbox(List.of(event));
        RuntimeException markFailure = new IllegalStateException("database disconnected after broker ack");
        RuntimeException releaseFailure = new IllegalStateException("database still disconnected");
        outbox.markFailure = markFailure;
        outbox.releaseFailure = releaseFailure;
        MessagePublicationRelay relay = new DefaultMessagePublicationRelay(outbox, ignored -> { }, FIXED_CLOCK);

        assertThatThrownBy(() -> relay.relay(1))
            .isSameAs(markFailure)
            .satisfies(failure -> assertThat(failure.getSuppressed()).containsExactly(releaseFailure));
        assertThat(outbox.unpublishedBacklogCount()).isEqualTo(1);
    }

    @Test
    void recoversUnattemptedBatchEventsWhenTheirClaimLeaseExpires() {
        MessagePublished first = event();
        MessagePublished second = event();
        RecordingOutbox outbox = new RecordingOutbox(List.of(first, second));
        MessagePublicationRelay failing = new DefaultMessagePublicationRelay(outbox, ignored -> {
            throw new IllegalStateException("broker unavailable");
        }, FIXED_CLOCK);

        assertThatThrownBy(() -> failing.relay(2)).hasMessage("broker unavailable");
        List<MessagePublished> dispatched = new ArrayList<>();
        MessagePublicationRelay retry = new DefaultMessagePublicationRelay(
            outbox, dispatched::add, Clock.offset(FIXED_CLOCK, Duration.ofSeconds(5)));
        assertThat(retry.relay(2)).isEqualTo(1);
        assertThat(dispatched).containsExactly(first);
        MessagePublicationRelay leaseRecovery = new DefaultMessagePublicationRelay(
            outbox, dispatched::add, Clock.offset(FIXED_CLOCK, Duration.ofSeconds(30)));

        assertThat(leaseRecovery.relay(2)).isEqualTo(1);
        assertThat(dispatched).containsExactly(first, second);
        assertThat(outbox.unpublishedBacklogCount()).isZero();
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
        private final Map<UUID, Instant> eligibleAt = new HashMap<>();
        private final Set<UUID> published = new HashSet<>();
        private RuntimeException markFailure;
        private RuntimeException releaseFailure;

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
                .filter(event -> !published.contains(event.eventId()))
                .filter(event -> !eligibleAt.getOrDefault(event.eventId(), Instant.MIN).isAfter(claimedAt))
                .limit(limit)
                .peek(event -> eligibleAt.put(event.eventId(), claimedAt.plus(lease)))
                .map(event -> new ClaimedMessagePublication(event, claimToken))
                .toList();
        }

        @Override
        public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
            if (markFailure != null) {
                throw markFailure;
            }
            published.add(eventId);
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
            if (releaseFailure != null) {
                throw releaseFailure;
            }
            eligibleAt.put(eventId, failedAt.plus(retryDelay));
            released.add(eventId);
            releaseErrors.add(errorMessage);
            releaseRetryDelays.add(retryDelay);
        }

        @Override
        public long unpublishedBacklogCount() {
            return events.size() - published.size();
        }
    }
}
