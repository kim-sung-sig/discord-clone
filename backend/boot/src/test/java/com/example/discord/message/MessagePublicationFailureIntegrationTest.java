package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.discord.gateway.GatewayEventLog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.time.Clock;
import java.nio.file.Path;
import java.nio.file.Files;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.CyclicBarrier;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.ContainerCustomizer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import com.example.discord.gateway.InMemoryGatewayService;
import org.springframework.kafka.event.ConsumerPausedEvent;
import org.springframework.kafka.listener.ConsumerAwareRebalanceListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.RecordInterceptor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Import(MessagePublicationFailureIntegrationTest.ListenerSignals.class)
@ActiveProfiles({"postgres", "kafka", "legacy-auth"})
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_MESSAGE_PIPELINE_FAILURE_TESTS", matches = "true")
@Testcontainers
class MessagePublicationFailureIntegrationTest {
    private static final String TOPIC_PREFIX = "message-pipeline-" + UUID.randomUUID().toString().replace("-", "");
    private static final BlockingQueue<ListenerSignal> LISTENER_SIGNALS = new LinkedBlockingQueue<>();
    private static final BlockingQueue<Boolean> PAUSE_SIGNALS = new LinkedBlockingQueue<>();
    private static final CountDownLatch GATEWAY_PARTITION_ASSIGNED = new CountDownLatch(1);
    private static final ObjectMapper EVENT_MAPPER = new ObjectMapper();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("discord")
        .withUsername("message_pipeline")
        .withPassword("message_pipeline_password");

    @DynamicPropertySource
    static void pipelineProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl() + "?connectTimeout=1&socketTimeout=1");
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("discord.kafka.topic-prefix", () -> TOPIC_PREFIX);
        registry.add("discord.message.outbox-relay-delay-ms", () -> 3_600_000);
        registry.add("spring.kafka.bootstrap-servers", () -> System.getenv().getOrDefault(
            "SPRING_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:29092"));
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MessageLookupPort messages;
    @Autowired private GatewayEventLog gatewayEvents;
    @Autowired private KafkaTemplate<String, String> kafka;
    @Autowired private MessagePublicationRelayWorker relayWorker;
    @Autowired private MeterRegistry metrics;
    @Autowired private KafkaListenerEndpointRegistry listenerRegistry;
    @Autowired private ObjectMapper objectMapper;
    private final Map<Process, Path> processArgumentFiles = new java.util.HashMap<>();

    @BeforeEach
    void createTopicsBeforeSending() throws Exception {
        LISTENER_SIGNALS.clear();
        messageConsumer().resume();
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers());
        try (AdminClient admin = AdminClient.create(properties)) {
            List<String> topics = List.of(
                TOPIC_PREFIX + ".message.published.v1",
                TOPIC_PREFIX + ".message.published.v1.dead-letter");
            var existing = admin.listTopics().names().get();
            List<NewTopic> missing = topics.stream()
                .filter(topic -> !existing.contains(topic))
                .map(topic -> new NewTopic(topic, 1, (short) 1))
                .toList();
            if (!missing.isEmpty()) {
                try {
                    admin.createTopics(missing).all().get();
                } catch (ExecutionException exception) {
                    // A listener subscription can auto-create the topic between listTopics and createTopics.
                    if (!(exception.getCause() instanceof TopicExistsException)) {
                        throw exception;
                    }
                }
            }
        }
        assertThat(GATEWAY_PARTITION_ASSIGNED.await(20, TimeUnit.SECONDS))
            .as("gateway message listener partition assignment")
            .isTrue();
    }

    @Test
    void brokenJsonDeadLettersAndDoesNotBlockTheNextRecord() throws Exception {
        assertPoisonDoesNotBlockNextRecord("{");
    }

    @Test
    void jsonNullDeadLettersAndDoesNotBlockTheNextRecord() throws Exception {
        assertPoisonDoesNotBlockNextRecord("null");
    }

    @Test
    void tombstoneDeadLettersAndDoesNotBlockTheNextRecord() throws Exception {
        assertPoisonDoesNotBlockNextRecord(null);
    }

    private void assertPoisonDoesNotBlockNextRecord(String poison) throws Exception {
        UUID messageId = UUID.fromString(createMessageThroughApi());
        relayWorker.relayPendingPublications();
        PublicationPayload next = publicationPayload(messageId);
        String key = "poison-" + UUID.randomUUID();
        String topic = TOPIC_PREFIX + ".message.published.v1";
        kafka.send(topic, key, poison).get(10, TimeUnit.SECONDS);
        kafka.send(topic, key, next.payload()).get(10, TimeUnit.SECONDS);
        awaitListener("gateway-service-v1", next.eventId(), true);
        assertThat(awaitDeadLettersForKey(key, 2)).allSatisfy(value ->
            assertThat(value).contains("payloadSha256Prefix").doesNotContain("pipeline-check"));
        assertThat(inboxCount(next.eventId())).isEqualTo(1);
    }

    @Test
    void inboxInsertFailureRetriesWithoutDuplicatingTheDurableGatewayEvent() throws Exception {
        UUID messageId = UUID.fromString(createMessageThroughApi());
        relayWorker.relayPendingPublications();
        PublicationPayload publication = publicationPayload(messageId);
        String fault = "pipeline_inbox_fault_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("""
            CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
            BEGIN
              IF NEW.event_id = '%s'::uuid THEN
                RAISE EXCEPTION 'task-owned inbox insertion failure';
              END IF;
              RETURN NEW;
            END $$
            """.formatted(fault, publication.eventId()));
        jdbc.execute("CREATE TRIGGER " + fault + " BEFORE INSERT ON consumer_inbox FOR EACH ROW EXECUTE FUNCTION " + fault + "()");
        try {
            kafka.send(TOPIC_PREFIX + ".message.published.v1", publication.channelId().toString(), publication.payload()).get();
            awaitListener("gateway-service-v1", publication.eventId(), false);
            assertThat(gatewayCount(publication.eventId())).isEqualTo(1);
            assertThat(inboxCount(publication.eventId())).isZero();
        } finally {
            jdbc.execute("DROP FUNCTION " + fault + "() CASCADE");
        }
        awaitListener("gateway-service-v1", publication.eventId(), true);
        assertThat(gatewayCount(publication.eventId())).isEqualTo(1);
        assertThat(inboxCount(publication.eventId())).isEqualTo(1);
    }

    @Autowired private InMemoryGatewayService gateway;
    @Autowired private JdbcMessagePublicationInbox durableInbox;
    @Autowired private MessagePublicationOutboxQueue durableOutbox;

    @Test
    void simultaneousDuplicateConsumersShareOneDurableGatewayEventAndInboxEntry() throws Exception {
        UUID messageId = UUID.fromString(createMessageThroughApi());
        relayWorker.relayPendingPublications();
        PublicationPayload publication = publicationPayload(messageId);
        CyclicBarrier bothObservedUnprocessed = new CyclicBarrier(2);
        MessagePublicationInbox barrierInbox = new MessagePublicationInbox() {
            @Override
            public boolean isProcessed(UUID id) {
                boolean processed = durableInbox.isProcessed(id);
                try {
                    bothObservedUnprocessed.await(10, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
                return processed;
            }

            @Override
            public void markProcessed(UUID id) {
                durableInbox.markProcessed(id);
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> new KafkaMessagePublishedConsumer(
                objectMapper, messages, gateway, barrierInbox, metrics).consume(publication.payload()));
            var second = executor.submit(() -> new KafkaMessagePublishedConsumer(
                objectMapper, messages, gateway, barrierInbox, metrics).consume(publication.payload()));
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }
        assertThat(gatewayCount(publication.eventId())).isEqualTo(1);
        assertThat(inboxCount(publication.eventId())).isEqualTo(1);
    }

    @Test
    void realKafkaConnectionFailurePreservesOutboxUntilBrokerRecovery() throws Exception {
        relayWorker.relayPendingPublications();
        UUID messageId = UUID.fromString(createMessageThroughApi());
        UUID eventId = jdbc.queryForObject(
            "SELECT event_id FROM message_publication_outbox WHERE message_id = ?", UUID.class, messageId);
        try (ServerSocket unavailableBroker = new ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())) {
            Map<String, Object> properties = new java.util.HashMap<>();
            properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "127.0.0.1:" + unavailableBroker.getLocalPort());
            properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            properties.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 500);
            properties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 500);
            properties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 1_000);
            properties.put(ProducerConfig.RETRIES_CONFIG, 0);
            DefaultKafkaProducerFactory<String, String> factory = new DefaultKafkaProducerFactory<>(properties);
            try {
                KafkaMessagePublishedDispatcher failedDispatcher = new KafkaMessagePublishedDispatcher(
                    new KafkaTemplate<>(factory), objectMapper, messages, Clock.systemUTC(), TOPIC_PREFIX, 1_000);
                var relay = new DefaultMessagePublicationRelay(durableOutbox, failedDispatcher, Clock.systemUTC());
                assertThatThrownBy(() -> relay.relay(1)).isInstanceOf(RuntimeException.class);
                assertThat(jdbc.queryForObject(
                    "SELECT published_at IS NULL AND claim_token IS NULL AND attempts = 1 "
                        + "FROM message_publication_outbox WHERE event_id = ?", Boolean.class, eventId)).isTrue();
                assertThat(inboxCount(eventId)).isZero();
            } finally {
                factory.destroy();
            }
        }
        Clock afterRetryDeadline = Clock.offset(Clock.systemUTC(), Duration.ofSeconds(6));
        var recoveredDispatcher = new KafkaMessagePublishedDispatcher(
            kafka, objectMapper, messages, afterRetryDeadline, TOPIC_PREFIX, 5_000);
        assertThat(new DefaultMessagePublicationRelay(durableOutbox, recoveredDispatcher, afterRetryDeadline).relay(1)).isEqualTo(1);
        awaitListener("gateway-service-v1", eventId, true);
        assertThat(jdbc.queryForObject(
            "SELECT published_at IS NOT NULL FROM message_publication_outbox WHERE event_id = ?", Boolean.class, eventId)).isTrue();
        assertThat(inboxCount(eventId)).isEqualTo(1);
        assertThat(gatewayCount(eventId)).isEqualTo(1);
    }

    private int inboxCount(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM consumer_inbox WHERE event_id = ?", Integer.class, eventId);
    }

    private int gatewayCount(UUID eventId) {
        return jdbc.queryForObject("SELECT count(*) FROM gateway_event_log WHERE event_id = ?", Integer.class, eventId);
    }

    @Test
    void hardKilledConsumerReplaysFromKafkaUsingTheSameDurableDatabase() throws Exception {
        UUID messageId = UUID.fromString(createMessageThroughApi());
        relayWorker.relayPendingPublications();
        PublicationPayload publication = publicationPayload(messageId);
        MessageListenerContainer parentConsumer = messageConsumer();
        parentConsumer.stop();
        Process crashed = null;
        Process restarted = null;
        var outputReader = Executors.newSingleThreadExecutor();
        try {
            crashed = startConsumerProcess(publication.eventId(), true);
            Process firstProcess = crashed;
            var checkpoint = outputReader.submit(() -> {
                awaitProcessMarker(firstProcess, "PIPELINE_PROCESS_BLOCKED");
                return null;
            });
            kafka.send(TOPIC_PREFIX + ".message.published.v1", publication.channelId().toString(), publication.payload()).get();
            checkpoint.get(90, TimeUnit.SECONDS);
            assertThat(gatewayCount(publication.eventId())).isEqualTo(1);
            assertThat(inboxCount(publication.eventId())).isZero();
            crashed.destroyForcibly();
            assertThat(crashed.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(crashed.exitValue()).isNotZero();

            restarted = startConsumerProcess(publication.eventId(), false);
            Process recoveryProcess = restarted;
            outputReader.submit(() -> {
                awaitProcessMarker(recoveryProcess, "PIPELINE_PROCESS_COMPLETED");
                return null;
            }).get(90, TimeUnit.SECONDS);
            assertThat(gatewayCount(publication.eventId())).isEqualTo(1);
            assertThat(inboxCount(publication.eventId())).isEqualTo(1);
        } finally {
            stopOwnedProcess(crashed);
            stopOwnedProcess(restarted);
            outputReader.shutdownNow();
            parentConsumer.start();
        }
    }

    private Process startConsumerProcess(UUID eventId, boolean blockBeforeCompletion) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        command.add("-cp");
        command.add(Files.readString(Path.of(System.getProperty("message.pipeline.test.classpath.file"))).trim());
        command.add(MessagePublicationConsumerProcess.class.getName());
        command.add("--server.port=0");
        command.add("--spring.profiles.active=postgres,kafka,legacy-auth");
        command.add("--spring.datasource.url=" + POSTGRES.getJdbcUrl());
        command.add("--spring.datasource.username=" + POSTGRES.getUsername());
        command.add("--spring.datasource.password=" + POSTGRES.getPassword());
        command.add("--spring.kafka.bootstrap-servers=" + bootstrapServers());
        command.add("--spring.kafka.consumer.auto-offset-reset=earliest");
        command.add("--spring.kafka.consumer.properties.session.timeout.ms=6000");
        command.add("--spring.kafka.consumer.properties.heartbeat.interval.ms=2000");
        command.add("--discord.kafka.topic-prefix=" + TOPIC_PREFIX);
        command.add("--discord.message.outbox-relay-delay-ms=3600000");
        command.add("--pipeline.process.event-id=" + eventId);
        command.add("--pipeline.process.block=" + blockBeforeCompletion);
        Path arguments = Files.createTempFile("message-pipeline-child-", ".args");
        Files.writeString(arguments, command.stream().skip(1)
            .map(argument -> "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
            .collect(java.util.stream.Collectors.joining(System.lineSeparator())));
        try {
            Process child = new ProcessBuilder(command.getFirst(), "@" + arguments).redirectErrorStream(true).start();
            processArgumentFiles.put(child, arguments);
            return child;
        } catch (Exception failure) {
            Files.deleteIfExists(arguments);
            throw failure;
        }
    }

    private static void awaitProcessMarker(Process process, String marker) throws Exception {
        List<String> tail = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), java.nio.charset.StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(marker)) {
                    return;
                }
                tail.add(line);
                if (tail.size() > 20) {
                    tail.removeFirst();
                }
            }
        }
        throw new AssertionError("child consumer exited before " + marker + ": " + tail);
    }

    private void stopOwnedProcess(Process process) throws Exception {
        if (process != null) {
            try {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    assertThat(process.waitFor(10, TimeUnit.SECONDS)).as("task-owned consumer process terminated").isTrue();
                }
            } finally {
                Path arguments = processArgumentFiles.remove(process);
                if (arguments != null) {
                    Files.deleteIfExists(arguments);
                }
            }
        }
    }

    private List<String> awaitDeadLettersForKey(String key, int expectedCount) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "message-pipeline-poison-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC_PREFIX + ".message.published.v1.dead-letter"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            List<String> matching = new ArrayList<>();
            while (System.nanoTime() < deadline) {
                for (var record : consumer.poll(Duration.ofSeconds(1))) {
                    if (key.equals(record.key())) {
                        matching.add(record.value());
                        if (matching.size() == expectedCount) {
                            return List.copyOf(matching);
                        }
                    }
                }
            }
        }
        throw new AssertionError("expected " + expectedCount + " dead-letter records for key " + key);
    }

    @Test
    void apiPublicationSurvivesListenerRestartAndDuplicateDelivery() throws Exception {
        String messageId = createMessageThroughApi();
        relayWorker.relayPendingPublications();
        UUID id = UUID.fromString(messageId);

        UUID eventId = jdbc.queryForObject(
            "SELECT event_id FROM message_publication_outbox WHERE message_id = ?", UUID.class, id);
        awaitListener("gateway-service-v1", eventId, true);
        String correlationId = jdbc.queryForObject(
            "SELECT correlation_id FROM message_publication_outbox WHERE event_id = ?", String.class, eventId);
        Instant occurredAt = jdbc.queryForObject(
            "SELECT occurred_at FROM message_publication_outbox WHERE event_id = ?", (rs, row) -> rs.getTimestamp(1).toInstant(), eventId);
        Message message = messages.requireMessage(
            new ChannelMessageTarget(
                jdbc.queryForObject("SELECT guild_id FROM message_publication_outbox WHERE event_id = ?", UUID.class, eventId),
                jdbc.queryForObject("SELECT channel_id FROM message_publication_outbox WHERE event_id = ?", UUID.class, eventId)),
            id);
        MessagePublished event = new MessagePublished(
            eventId, id, message.author(), message.target(), message.mentions(), correlationId, occurredAt);
        String payload = objectMapper.writeValueAsString(MessagePublishedRecord.from(
            event, MessagePublishedRecord.payloadHashFor(event)));

        MessageListenerContainer container = listenerRegistry.getAllListenerContainers().stream()
            .filter(candidate -> "gateway-service-v1".equals(candidate.getGroupId()))
            .findFirst()
            .orElseThrow();
        container.stop();
        kafka.send(TOPIC_PREFIX + ".message.published.v1", message.channelId().toString(), payload).get();
        container.start();

        awaitListener("gateway-service-v1", eventId, true);
        assertThat(jdbc.queryForObject(
            "SELECT count(*) FROM consumer_inbox WHERE event_id = ?", Integer.class, eventId)).isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "duplicate").counter().count())
            .isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.end_to_end").timer().count()).isPositive();
        System.out.printf(Locale.ROOT, "MESSAGE_PIPELINE_EVIDENCE duplicate_events=%.0f end_to_end_ms=%.3f%n",
            metrics.find("discord.message.publication.consumer.events").tag("outcome", "duplicate").counter().count(),
            metrics.find("discord.message.publication.end_to_end").timer().totalTime(java.util.concurrent.TimeUnit.MILLISECONDS));
    }

    @Test
    void malformedKafkaEventIsSentToMetadataOnlyDeadLetterTopic() throws Exception {
        UUID eventId = UUID.randomUUID();
        String body = "{\"eventId\":\"" + eventId + "\",\"content\":\"secret-message-body\"}";
        double failuresBefore = metrics.counter(
            "discord.message.publication.consumer.events", "outcome", "failed").count();
        kafka.send(TOPIC_PREFIX + ".message.published.v1", "poison", body).get();
        awaitListener("gateway-service-v1", eventId, false);

        String deadLetter = awaitDeadLetters(TOPIC_PREFIX + ".message.published.v1", eventId, 1).getFirst();

        assertThat(deadLetter)
            .contains("payloadSha256Prefix", TOPIC_PREFIX + ".message.published.v1")
            .doesNotContain("secret-message-body", "content");
        assertThat(metrics.counter("discord.message.publication.consumer.events", "outcome", "failed").count())
            .isEqualTo(failuresBefore + 1);
    }

    @Test
    void consumerLagGaugeRisesWhilePausedAndFallsAfterDrain() throws Exception {
        UUID messageId = UUID.fromString(createMessageThroughApi());
        relayWorker.relayPendingPublications();
        UUID sourceEventId = jdbc.queryForObject(
            "SELECT event_id FROM message_publication_outbox WHERE message_id = ?", UUID.class, messageId);
        awaitListener("gateway-service-v1", sourceEventId, true);
        List<PublicationPayload> queued = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            queued.add(publicationPayload(messageId));
        }
        MessageListenerContainer container = messageConsumer();
        container.pause();
        assertThat(PAUSE_SIGNALS.poll(20, TimeUnit.SECONDS))
            .as("gateway listener pause acknowledgement")
            .isTrue();
        for (PublicationPayload publication : queued) {
            kafka.send(TOPIC_PREFIX + ".message.published.v1", publication.channelId().toString(), publication.payload()).get();
        }

        var consumerLag = metrics.find("discord.message.publication.consumer.lag").gauge();
        messagePublicationConsumerLag.refresh();
        assertThat(consumerLag.value()).isGreaterThan(0);
        double peakLag = metrics.find("discord.message.publication.consumer.lag").gauge().value();
        container.resume();
        for (PublicationPayload publication : queued) {
            awaitListener("gateway-service-v1", publication.eventId(), true);
        }
        container.pause();
        assertThat(PAUSE_SIGNALS.poll(20, TimeUnit.SECONDS)).isTrue();
        messagePublicationConsumerLag.refresh();
        assertThat(consumerLag.value()).isZero();

        System.out.printf(Locale.ROOT, "MESSAGE_PIPELINE_EVIDENCE consumer_lag_peak=%.0f consumer_lag_drained=%.0f queued_events=%d%n",
            peakLag, metrics.find("discord.message.publication.consumer.lag").gauge().value(), queued.size());
    }

    @Test
    void databaseConnectionFailureDeadLettersThenReplaysAfterDatabaseRecovery() throws Exception {
        UUID messageId = UUID.fromString(createMessageThroughApi());
        relayWorker.relayPendingPublications();
        UUID sourceEventId = jdbc.queryForObject(
            "SELECT event_id FROM message_publication_outbox WHERE message_id = ?", UUID.class, messageId);
        awaitListener("gateway-service-v1", sourceEventId, true);
        PublicationPayload publication = publicationPayload(messageId);
        String topic = TOPIC_PREFIX + ".message.published.v1";

        double failedBefore = metrics.counter(
            "discord.message.publication.consumer.events", "outcome", "failed").count();
        double retriesBefore = metrics.counter("discord.message.publication.consumer.retries").count();
        double deadLettersBefore = metrics.counter(
            "discord.message.publication.consumer.events", "outcome", "dead_letter").count();
        long failureStarted = System.nanoTime();
        var docker = DockerClientFactory.instance().client();
        docker.pauseContainerCmd(POSTGRES.getContainerId()).exec();
        List<String> deadLetters;
        try {
            assertThatThrownBy(() -> jdbc.queryForObject("SELECT 1", Integer.class)).isInstanceOf(RuntimeException.class);
            kafka.send(topic, publication.channelId().toString(), publication.payload()).get();
            awaitListenerFailures("gateway-service-v1", publication.eventId(), 3);
            deadLetters = awaitDeadLetters(topic, publication.eventId(), 2);
        } finally {
            docker.unpauseContainerCmd(POSTGRES.getContainerId()).exec();
        }
        long deadLetterMillis = Duration.ofNanos(System.nanoTime() - failureStarted).toMillis();
        assertThat(deadLetters).allSatisfy(deadLetter ->
            assertThat(deadLetter).contains("ListenerExecutionFailedException", publication.eventId().toString()));
        double failedAttempts = metrics.counter(
            "discord.message.publication.consumer.events", "outcome", "failed").count() - failedBefore;
        double retryCallbacks = metrics.counter("discord.message.publication.consumer.retries").count() - retriesBefore;
        double deadLetterRecords = metrics.counter(
            "discord.message.publication.consumer.events", "outcome", "dead_letter").count() - deadLettersBefore;
        assertThat(failedAttempts).isEqualTo(3);
        assertThat(retryCallbacks).isEqualTo(6);
        assertThat(deadLetterRecords).isEqualTo(2);
        long recoveryStarted = System.nanoTime();
        kafka.send(topic, publication.channelId().toString(), publication.payload()).get();
        awaitListener("gateway-service-v1", publication.eventId(), true);
        long recoveryMillis = Duration.ofNanos(System.nanoTime() - recoveryStarted).toMillis();
        assertThat(gatewayEvents.after(0, 500).stream()
            .filter(event -> publication.eventId().toString().equals(event.busEventId())).count()).isEqualTo(1);
        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "processed").counter().count())
            .isPositive();
        var endToEnd = metrics.find("discord.message.publication.end_to_end").timer();
        System.out.printf(Locale.ROOT,
            "MESSAGE_PIPELINE_EVIDENCE db_failure_attempts=%.0f retry_callbacks=%.0f dlq_records=%.0f dlq_after_failure_ms=%d recovery_to_inbox_ms=%d end_to_end_samples=%d end_to_end_total_ms=%.3f outbox_backlog=%.0f%n",
            failedAttempts,
            retryCallbacks,
            deadLetterRecords,
            deadLetterMillis,
            recoveryMillis,
            endToEnd.count(),
            endToEnd.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS),
            metrics.find("discord.message.publication.outbox.backlog").gauge().value());
    }

    private String createMessageThroughApi() throws Exception {
        String username = "pipeline_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        var signup = mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s@example.com","username":"%s","displayName":"Pipeline","password":"correct horse battery staple"}
                    """.formatted(username, username)))
            .andExpect(status().isCreated())
            .andReturn();
        String token = JsonPath.read(signup.getResponse().getContentAsString(), "$.accessToken");
        String auth = "Bearer " + token;
        var guildResponse = mockMvc.perform(post("/api/guilds")
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"pipeline\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        String guildId = JsonPath.read(guildResponse.getResponse().getContentAsString(), "$.id");
        var channelResponse = mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"general\",\"type\":\"GUILD_TEXT\",\"parentId\":null}"))
            .andExpect(status().isCreated())
            .andReturn();
        String channelId = JsonPath.read(channelResponse.getResponse().getContentAsString(), "$.id");
        var messageResponse = mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", auth)
                .header("X-Request-Id", "pipeline-" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"pipeline-check\",\"idempotencyKey\":\"" + UUID.randomUUID() + "\"}"))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(messageResponse.getResponse().getContentAsString(), "$.id");
    }

    private PublicationPayload publicationPayload(UUID messageId) throws Exception {
        UUID sourceEventId = jdbc.queryForObject(
            "SELECT event_id FROM message_publication_outbox WHERE message_id = ?", UUID.class, messageId);
        UUID eventId = UUID.randomUUID();
        UUID guildId = jdbc.queryForObject(
            "SELECT guild_id FROM message_publication_outbox WHERE event_id = ?", UUID.class, sourceEventId);
        UUID channelId = jdbc.queryForObject(
            "SELECT channel_id FROM message_publication_outbox WHERE event_id = ?", UUID.class, sourceEventId);
        String correlationId = jdbc.queryForObject(
            "SELECT correlation_id FROM message_publication_outbox WHERE event_id = ?", String.class, sourceEventId);
        Instant occurredAt = jdbc.queryForObject(
            "SELECT occurred_at FROM message_publication_outbox WHERE event_id = ?", (rs, row) -> rs.getTimestamp(1).toInstant(), sourceEventId);
        Message message = messages.requireMessage(new ChannelMessageTarget(guildId, channelId), messageId);
        MessagePublished event = new MessagePublished(
            eventId, messageId, message.author(), message.target(), message.mentions(), correlationId, occurredAt);
        String payload = objectMapper.writeValueAsString(MessagePublishedRecord.from(
            event, MessagePublishedRecord.payloadHashFor(event)));
        return new PublicationPayload(eventId, channelId, payload);
    }

    private List<String> awaitDeadLetters(String expectedTopic, UUID expectedEventId, int expectedCount) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "message-pipeline-dlq-test-" + UUID.randomUUID());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(TOPIC_PREFIX + ".message.published.v1.dead-letter"));
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            List<String> matching = new java.util.ArrayList<>();
            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofSeconds(1));
                for (var record : records) {
                    String value = record.value();
                    if (value.contains("\"topic\":\"" + expectedTopic + "\"")
                        && value.contains("\"eventId\":\"" + expectedEventId + "\"")) {
                        matching.add(value);
                        if (matching.size() == expectedCount) {
                            return List.copyOf(matching);
                        }
                    }
                }
            }
        }
        throw new AssertionError("expected " + expectedCount + " message publication dead-letter records for " + expectedEventId);
    }

    private void awaitListener(String groupId, UUID eventId, boolean success) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (System.nanoTime() < deadline) {
            ListenerSignal signal = LISTENER_SIGNALS.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (signal == null) {
                break;
            }
            if (signal.groupId().equals(groupId)
                && eventId.toString().equals(signal.eventId())
                && (signal.failure() == null) == success) {
                return;
            }
        }
        throw new AssertionError("listener did not complete event " + eventId + " for group " + groupId);
    }

    private void awaitListenerFailures(String groupId, UUID eventId, int expected) throws InterruptedException {
        int failures = 0;
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (failures < expected && System.nanoTime() < deadline) {
            ListenerSignal signal = LISTENER_SIGNALS.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (signal == null) {
                break;
            }
            if (signal.groupId().equals(groupId)
                && eventId.toString().equals(signal.eventId())
                && signal.failure() != null) {
                failures++;
            }
        }
        assertThat(failures).as("failed listener attempts for " + groupId).isEqualTo(expected);
    }

    private MessageListenerContainer messageConsumer() {
        return listenerRegistry.getAllListenerContainers().stream()
            .filter(candidate -> "gateway-service-v1".equals(candidate.getGroupId()))
            .findFirst()
            .orElseThrow();
    }

    private static String bootstrapServers() {
        return System.getenv().getOrDefault("SPRING_KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:29092");
    }

    private record PublicationPayload(UUID eventId, UUID channelId, String payload) { }

    @Autowired private MessagePublicationConsumerLag messagePublicationConsumerLag;

    private record ListenerSignal(String groupId, String eventId, Exception failure) { }

    @TestConfiguration(proxyBeanMethods = false)
    static class ListenerSignals {
        @Bean
        ContainerCustomizer<Object, Object, ConcurrentMessageListenerContainer<Object, Object>> listenerSignalCustomizer() {
            return container -> {
                String groupId = container.getGroupId() == null ? container.getListenerId() : container.getGroupId();
                container.setRecordInterceptor(new RecordInterceptor<>() {
                    @Override
                    public ConsumerRecord<Object, Object> intercept(
                        ConsumerRecord<Object, Object> record,
                        Consumer<Object, Object> consumer
                    ) {
                        return record;
                    }

                    @Override
                    public void success(ConsumerRecord<Object, Object> record, Consumer<Object, Object> consumer) {
                        LISTENER_SIGNALS.offer(new ListenerSignal(groupId, eventId(String.valueOf(record.value())), null));
                    }

                    @Override
                    public void failure(
                        ConsumerRecord<Object, Object> record,
                        Exception failure,
                        Consumer<Object, Object> consumer
                    ) {
                        LISTENER_SIGNALS.offer(new ListenerSignal(groupId, eventId(String.valueOf(record.value())), failure));
                    }
                });
                container.getContainerProperties().setConsumerRebalanceListener(new ConsumerAwareRebalanceListener() {
                    @Override
                    public void onPartitionsAssigned(Consumer<?, ?> consumer, java.util.Collection<TopicPartition> partitions) {
                        if (partitions.stream().anyMatch(partition ->
                            (TOPIC_PREFIX + ".message.published.v1").equals(partition.topic()))) {
                            GATEWAY_PARTITION_ASSIGNED.countDown();
                        }
                    }
                });
            };
        }

        @Bean
        ApplicationListener<ConsumerPausedEvent> listenerPauseSignals() {
            return event -> {
                MessageListenerContainer container = event.getContainer(MessageListenerContainer.class);
                if (container != null) {
                    PAUSE_SIGNALS.offer(true);
                }
            };
        }
    }

    private static String eventId(String payload) {
        try {
            return EVENT_MAPPER.readTree(payload).path("eventId").asText();
        } catch (Exception ignored) {
            return "";
        }
    }
}
