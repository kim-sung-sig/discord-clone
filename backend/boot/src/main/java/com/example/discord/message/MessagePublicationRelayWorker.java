package com.example.discord.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class MessagePublicationRelayWorker {
    private final MessagePublicationRelay relay;
    private final int batchSize;

    MessagePublicationRelayWorker(
        MessagePublicationRelay relay,
        @Value("${discord.message.outbox-relay-batch-size:50}") int batchSize
    ) {
        this.relay = relay;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${discord.message.outbox-relay-delay-ms:1000}")
    void relayPendingPublications() {
        relay.relay(batchSize);
    }
}
