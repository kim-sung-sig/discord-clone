package com.example.discord.message;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultMessagePublicationRelay implements MessagePublicationRelay {
    private static final Logger log = LoggerFactory.getLogger(DefaultMessagePublicationRelay.class);
    private static final int MAX_BATCH_SIZE = 100;

    private final MessagePublicationOutboxQueue outbox;
    private final MessagePublishedDispatcher dispatcher;
    private final Clock clock;
    private final Duration claimLease;
    private final Duration retryDelay;

    public DefaultMessagePublicationRelay(
        MessagePublicationOutboxQueue outbox,
        MessagePublishedDispatcher dispatcher,
        Clock clock
    ) {
        this(outbox, dispatcher, clock, Duration.ofSeconds(30), Duration.ofSeconds(5));
    }

    public DefaultMessagePublicationRelay(
        MessagePublicationOutboxQueue outbox,
        MessagePublishedDispatcher dispatcher,
        Clock clock,
        Duration claimLease
    ) {
        this(outbox, dispatcher, clock, claimLease, Duration.ofSeconds(5));
    }

    public DefaultMessagePublicationRelay(
        MessagePublicationOutboxQueue outbox,
        MessagePublishedDispatcher dispatcher,
        Clock clock,
        Duration claimLease,
        Duration retryDelay
    ) {
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.claimLease = Objects.requireNonNull(claimLease, "claimLease must not be null");
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay must not be null");
        if (retryDelay.isZero() || retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must be positive");
        }
    }

    @Override
    public int relay(int limit) {
        int delivered = 0;
        int batchSize = batchSize(limit);
        log.info("Relaying message publications started requestedLimit={} batchSize={}", limit, batchSize);
        for (ClaimedMessagePublication publication : outbox.claimPendingPublications(
            batchSize,
            clock.instant(),
            claimLease
        )) {
            MessagePublished event = publication.event();
            try {
                log.debug("Dispatching message publication eventId={} messageId={}", event.eventId(), event.messageId());
                dispatcher.dispatch(event);
                outbox.markPublished(event.eventId(), publication.claimToken(), clock.instant());
                log.info("Message publication marked published eventId={} messageId={}", event.eventId(), event.messageId());
            } catch (RuntimeException exception) {
                log.warn(
                    "Message publication dispatch failed eventId={} messageId={}",
                    event.eventId(),
                    event.messageId(),
                    exception
                );
                outbox.releaseFailed(
                    event.eventId(),
                    publication.claimToken(),
                    exception.getMessage(),
                    clock.instant(),
                    retryDelay
                );
                throw exception;
            }
            delivered++;
        }
        log.info("Relaying message publications finished delivered={}", delivered);
        return delivered;
    }

    private static int batchSize(int requestedLimit) {
        if (requestedLimit < 1) {
            return 1;
        }
        return Math.min(requestedLimit, MAX_BATCH_SIZE);
    }
}
