package com.example.discord.messageservice;

import com.example.discord.message.DefaultMessagePublicationRelay;
import com.example.discord.message.MessagePublicationOutboxQueue;
import com.example.discord.message.MessagePublicationRelay;
import com.example.discord.message.MessagePublishedDispatcher;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@Profile("postgres & kafka")
@EnableScheduling
class MessagePublicationRuntimeConfiguration {
    @Bean
    MessagePublishedDispatcher messagePublishedDispatcher(
        KafkaTemplate<String, String> kafka,
        ObjectMapper objectMapper,
        @Value("${discord.kafka.topic-prefix:discord}") String topicPrefix,
        @Value("${discord.kafka.message-publish-timeout-ms:5000}") long timeoutMillis
    ) {
        return new KafkaMessagePublishedDispatcher(kafka, objectMapper, Clock.systemUTC(), topicPrefix, timeoutMillis);
    }

    @Bean
    MessagePublicationRelay messagePublicationRelay(
        MessagePublicationOutboxQueue outbox,
        MessagePublishedDispatcher dispatcher,
        @Value("${discord.message.outbox-relay-retry-delay-ms:5000}") long retryDelayMillis
    ) {
        return new DefaultMessagePublicationRelay(
            outbox,
            dispatcher,
            Clock.systemUTC(),
            Duration.ofSeconds(30),
            Duration.ofMillis(retryDelayMillis)
        );
    }

    @Bean
    MessagePublicationRelayWorker messagePublicationRelayWorker(
        MessagePublicationRelay relay,
        @Value("${discord.message.outbox-relay-batch-size:50}") int batchSize
    ) {
        return new MessagePublicationRelayWorker(relay, batchSize);
    }

    static final class MessagePublicationRelayWorker {
        private final MessagePublicationRelay relay;
        private final int batchSize;

        MessagePublicationRelayWorker(MessagePublicationRelay relay, int batchSize) {
            this.relay = relay;
            this.batchSize = batchSize;
        }

        @Scheduled(fixedDelayString = "${discord.message.outbox-relay-delay-ms:1000}")
        void relayPendingPublications() {
            relay.relay(batchSize);
        }
    }
}
