package com.example.discord.gateway;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_CENTRAL_KAFKA_GATEWAY_SMOKE", matches = "true")
class CentralKafkaGatewayEventBusSmokeTest {
    @Test
    void twoGatewayNodesExchangeEventsThroughCentralKafka() throws Exception {
        String bootstrapServers = envOrDefault("SPRING_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:29092");
        String topicPrefix = "discord-smoke-" + UUID.randomUUID().toString().replace("-", "");
        String topic = topicPrefix + ".gateway.events";
        createTopic(bootstrapServers, topic);

        DefaultKafkaProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(
            producerProps(bootstrapServers)
        );
        KafkaTemplate<String, String> kafka = new KafkaTemplate<>(producerFactory);
        KafkaGatewayEventBus nodeA = new KafkaGatewayEventBus(
            kafka,
            new ObjectMapper(),
            new SimpleMeterRegistry(),
            Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC),
            "node-a",
            topicPrefix,
            1
        );
        KafkaGatewayEventBus nodeB = new KafkaGatewayEventBus(
            kafka,
            new ObjectMapper(),
            new SimpleMeterRegistry(),
            Clock.systemUTC(),
            "node-b",
            topicPrefix,
            1
        );
        CopyOnWriteArrayList<GatewayBusEvent> received = new CopyOnWriteArrayList<>();
        nodeB.addEventListener(received::add);

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps(bootstrapServers))) {
            consumer.subscribe(List.of(topic));
            UUID guildId = UUID.randomUUID();
            UUID channelId = UUID.randomUUID();
            GatewayBusEvent published = nodeA.publish(new GatewayBusPublishCommand(
                "MESSAGE_CREATE",
                guildId,
                channelId,
                Map.of("content", "central kafka smoke")
            ));
            kafka.flush();

            Instant deadline = Instant.now().plusSeconds(30);
            while (received.isEmpty() && Instant.now().isBefore(deadline)) {
                consumer.poll(Duration.ofMillis(500))
                    .forEach(record -> nodeB.handleMessage(record.value()));
            }

            assertThat(received).hasSize(1);
            assertThat(received.get(0).eventId()).isEqualTo(published.eventId());
            assertThat(received.get(0).type()).isEqualTo("MESSAGE_CREATE");
            assertThat(received.get(0).guildId()).isEqualTo(guildId);
            assertThat(received.get(0).channelId()).isEqualTo(channelId);
            assertThat(received.get(0).payload()).containsEntry("content", "central kafka smoke");
        } finally {
            producerFactory.destroy();
        }
    }

    private static void createTopic(String bootstrapServers, String topic) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
            AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            bootstrapServers
        ))) {
            try {
                admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get(30, SECONDS);
            } catch (ExecutionException exception) {
                if (!(exception.getCause() instanceof TopicExistsException)) {
                    throw exception;
                }
            }
        }
    }

    private static Map<String, Object> producerProps(String bootstrapServers) {
        return Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all"
        );
    }

    private static Properties consumerProps(String bootstrapServers) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "gateway-smoke-node-b-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        return properties;
    }

    private static String envOrDefault(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
