package com.example.discord.message;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class MessagePublicationRelayWorker {
    private final MessagePublicationRelay relay;
    private final MessagePublicationOutboxQueue outbox;
    private final MeterRegistry meterRegistry;
    private final int batchSize;

    MessagePublicationRelayWorker(
        MessagePublicationRelay relay,
        MessagePublicationOutboxQueue outbox,
        MeterRegistry meterRegistry,
        @Value("${discord.message.outbox-relay-batch-size:50}") int batchSize
    ) {
        this.relay = relay;
        this.outbox = outbox;
        this.meterRegistry = meterRegistry;
        this.batchSize = batchSize;
        Gauge.builder("discord.message.publication.outbox.backlog", outbox, MessagePublicationOutboxQueue::unpublishedBacklogCount)
            .description("Unpublished message events waiting in the Outbox")
            .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${discord.message.outbox-relay-delay-ms:1000}")
    void relayPendingPublications() {
        String outcome = "published";
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            relay.relay(batchSize);
        } catch (RuntimeException failure) {
            outcome = "failed";
            throw failure;
        } finally {
            sample.stop(Timer.builder("discord.message.publication.relay")
                .description("Message Outbox relay cycle duration")
                .tag("outcome", outcome)
                .register(meterRegistry));
        }
    }
}
