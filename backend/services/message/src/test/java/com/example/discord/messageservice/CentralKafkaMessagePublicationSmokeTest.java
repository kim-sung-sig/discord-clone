package com.example.discord.messageservice;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.message.ClaimedMessagePublication;
import com.example.discord.message.DefaultMessagePublicationRelay;
import com.example.discord.message.MessagePublished;
import com.example.discord.message.MessagePublicationOutboxQueue;
import com.example.discord.message.MessagePublicationRelay;
import com.example.discord.message.MessagePublishedRecord;
import com.example.discord.message.UserMessageAuthor;
import com.example.discord.message.ChannelMessageTarget;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_CENTRAL_KAFKA_MESSAGE_SMOKE", matches = "true")
class CentralKafkaMessagePublicationSmokeTest {
    @Test
    void relayWaitsForCentralBrokerAndPreservesEventId() throws Exception {
        String bootstrap = System.getenv().getOrDefault("SPRING_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:29092");
        String prefix = "discord-message-smoke-" + UUID.randomUUID().toString().replace("-", "");
        String topic = prefix + ".message.published.v1";
        try (AdminClient admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }

        DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(producerProperties(bootstrap));
        KafkaTemplate<String, String> kafka = new KafkaTemplate<>(factory);
        MessagePublished event = event();
        RecordingQueue queue = new RecordingQueue(event);
        MessagePublicationRelay relay = new DefaultMessagePublicationRelay(
            queue,
            new KafkaMessagePublishedDispatcher(kafka, new ObjectMapper().findAndRegisterModules(), Clock.systemUTC(), prefix, 10_000),
            Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC),
            Duration.ofSeconds(30),
            Duration.ofSeconds(1)
        );

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties(bootstrap))) {
            consumer.subscribe(List.of(topic));
            assertThat(relay.relay(1)).isEqualTo(1);
            String value = null;
            Instant deadline = Instant.now().plusSeconds(10);
            while (value == null && Instant.now().isBefore(deadline)) {
                var records = consumer.poll(Duration.ofMillis(250));
                if (!records.isEmpty()) value = records.iterator().next().value();
            }
            assertThat(value).isNotNull();
            MessagePublishedRecord record = new ObjectMapper().findAndRegisterModules()
                .readValue(value, MessagePublishedRecord.class);
            assertThat(record.eventId()).isEqualTo(event.eventId());
            assertThat(record.channelId()).isEqualTo(((ChannelMessageTarget) event.target()).channelId());
            assertThat(queue.marked).containsExactly(event.eventId());
        } finally {
            factory.destroy();
        }
    }

    private static MessagePublished event() {
        return new MessagePublished(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            List.of(),
            "central-smoke",
            Instant.parse("2026-08-22T00:00:00Z")
        );
    }

    private static Map<String, Object> producerProperties(String bootstrap) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        properties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return properties;
    }

    private static Properties consumerProperties(String bootstrap) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "message-smoke-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return properties;
    }

    private static final class RecordingQueue implements MessagePublicationOutboxQueue {
        private final MessagePublished event;
        private final UUID claimToken = UUID.randomUUID();
        private final List<UUID> marked = new CopyOnWriteArrayList<>();

        private RecordingQueue(MessagePublished event) {
            this.event = event;
        }

        @Override
        public List<ClaimedMessagePublication> claimPendingPublications(int limit, Instant claimedAt, Duration lease) {
            return List.of(new ClaimedMessagePublication(event, claimToken));
        }

        @Override
        public void markPublished(UUID eventId, UUID claimToken, Instant publishedAt) {
            marked.add(eventId);
        }

        @Override
        public void releaseFailed(UUID eventId, UUID claimToken, String errorMessage, Instant failedAt, Duration retryDelay) {
        }

        @Override
        public long unpublishedBacklogCount() {
            return marked.isEmpty() ? 1 : 0;
        }
    }
}
