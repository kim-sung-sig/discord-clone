package com.example.discord.message;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;

public final class DefaultMessagePublicationRelay implements MessagePublicationRelay {
    private static final int MAX_BATCH_SIZE = 100;

    private final MessagePublicationOutboxQueue outbox;
    private final MessagePublishedDispatcher dispatcher;
    private final Clock clock;
    private final Duration claimLease;

    public DefaultMessagePublicationRelay(
        MessagePublicationOutboxQueue outbox,
        MessagePublishedDispatcher dispatcher,
        Clock clock
    ) {
        this(outbox, dispatcher, clock, Duration.ofSeconds(30));
    }

    public DefaultMessagePublicationRelay(
        MessagePublicationOutboxQueue outbox,
        MessagePublishedDispatcher dispatcher,
        Clock clock,
        Duration claimLease
    ) {
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.claimLease = Objects.requireNonNull(claimLease, "claimLease must not be null");
    }

    @Override
    public int relay(int limit) {
        int delivered = 0;
        for (ClaimedMessagePublication publication : outbox.claimPendingPublications(
            batchSize(limit),
            clock.instant(),
            claimLease
        )) {
            MessagePublished event = publication.event();
            try {
                dispatcher.dispatch(event);
                outbox.markPublished(event.eventId(), publication.claimToken(), clock.instant());
            } catch (RuntimeException exception) {
                outbox.releaseFailed(event.eventId(), publication.claimToken(), exception.getMessage(), clock.instant());
                throw exception;
            }
            delivered++;
        }
        return delivered;
    }

    private static int batchSize(int requestedLimit) {
        if (requestedLimit < 1) {
            return 1;
        }
        return Math.min(requestedLimit, MAX_BATCH_SIZE);
    }
}
