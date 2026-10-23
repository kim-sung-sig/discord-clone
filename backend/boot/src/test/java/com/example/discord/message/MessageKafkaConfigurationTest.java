package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class MessageKafkaConfigurationTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"{", "not-json secret-body", "{\"eventId\":", "null", "[]", "42", "\"text\""})
    void poisonPayloadAlwaysProducesSafeDeadLetterMetadata(String payload) throws Exception {
        var mapper = new ObjectMapper();
        var record = new ConsumerRecord<String, String>("discord.message.published.v1", 0, 7L, "key", payload);

        String encoded = MessageKafkaConfiguration.deadLetter(mapper, record, new IllegalArgumentException("secret exception"));

        var metadata = mapper.readTree(encoded);
        assertThat(metadata.path("eventId").asText()).isEmpty();
        assertThat(metadata.path("size").asInt()).isEqualTo(payload == null ? 0 : payload.length());
        assertThat(metadata.path("payloadSha256Prefix").asText()).hasSize(12);
        assertThat(encoded).doesNotContain("secret-body", "secret exception");
    }

    @Test
    void exhaustedRetryDlqContainsMetadataOnly() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "discord.message.published.v1",
            0,
            7L,
            "channel",
            "{\"eventId\":\"event-1\",\"content\":\"secret body\"}"
        );

        String deadLetter = MessageKafkaConfiguration.deadLetter(
            new ObjectMapper(),
            record,
            new IllegalStateException("secret exception")
        );

        assertThat(deadLetter)
            .contains("event-1", "payloadSha256Prefix", "discord.message.published.v1")
            .doesNotContain("secret body", "content", "secret exception");
    }

    @Test
    void retryHandlerIsProvidedForKafkaProfile() {
        KafkaTemplate<String, String> kafka = mock();
        MessageKafkaConfiguration configuration = new MessageKafkaConfiguration();

        assertThat(configuration.messageKafkaErrorHandler(kafka, new ObjectMapper(), new SimpleMeterRegistry(), "discord", 1000))
            .isNotNull();
    }

    @Test
    void deadLetterIsCountedOnlyAfterKafkaAcknowledgesIt() throws Exception {
        KafkaTemplate<String, String> kafka = mock();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        MessageKafkaConfiguration configuration = new MessageKafkaConfiguration();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("discord.message.published.v1", 0, 0L, "channel", "{}");

        String deadLetter = MessageKafkaConfiguration.deadLetter(new ObjectMapper(), record, new IllegalArgumentException("bad event"));
        when(kafka.send("discord.message.published.v1.dead-letter", "channel", deadLetter))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var handler = configuration.messageKafkaErrorHandler(kafka, new ObjectMapper(), metrics, "discord", 1000);

        handler.handleOne(new IllegalArgumentException("bad event"), record, mock(), mock());

        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "dead_letter").counter().count())
            .isEqualTo(1);
    }

    @Test
    void calculatesLagFromCommittedAndLatestMessageOffsetsOnly() {
        TopicPartition message = new TopicPartition("discord.message.published.v1", 0);
        TopicPartition unrelated = new TopicPartition("discord.gateway.events", 0);
        Map<TopicPartition, OffsetAndMetadata> committed = Map.of(
            message, new OffsetAndMetadata(4L),
            unrelated, new OffsetAndMetadata(2L));
        Map<TopicPartition, Long> latest = Map.of(message, 9L, unrelated, 100L);

        assertThat(MessagePublicationConsumerLag.calculateLag(
            java.util.Set.of(message, unrelated), committed, latest, "discord.message.published.v1"))
            .isEqualTo(5L);
    }

    @Test
    void doesNotReportNegativeLagWhenCommittedOffsetIsAheadOfLatest() {
        TopicPartition message = new TopicPartition("discord.message.published.v1", 0);

        assertThat(MessagePublicationConsumerLag.calculateLag(
            java.util.Set.of(message),
            Map.of(message, new OffsetAndMetadata(8L)),
            Map.of(message, 5L),
            "discord.message.published.v1"))
            .isZero();
    }

    @Test
    void countsRetryAndDeadLetterMetricsOnlyForMessagePublicationTopic() throws Exception {
        KafkaTemplate<String, String> kafka = mock();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        MessageKafkaConfiguration configuration = new MessageKafkaConfiguration();
        ConsumerRecord<String, String> unrelated = new ConsumerRecord<>("discord.authz.events", 0, 0L, "key", "{}");
        String deadLetter = MessageKafkaConfiguration.deadLetter(
            new ObjectMapper(), unrelated, new IllegalArgumentException("bad event"));
        when(kafka.send("discord.message.published.v1.dead-letter", "key", deadLetter))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var handler = configuration.messageKafkaErrorHandler(
            kafka, new ObjectMapper(), metrics, "discord", 1000);

        handler.handleOne(new IllegalArgumentException("bad event"), unrelated, mock(), mock());

        assertThat(metrics.find("discord.message.publication.consumer.events").tag("outcome", "dead_letter").counter())
            .isNull();
        assertThat(metrics.find("discord.message.publication.consumer.retries").counter()).isNull();
    }

    @Test
    void invalidMessagePublicationRecordSkipsRetriesButOtherListenersKeepRetrying() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        var handler = new MessageKafkaConfiguration().messageKafkaErrorHandler(
            kafka, new ObjectMapper(), new SimpleMeterRegistry(), "discord", 1000);
        ConsumerRecord<String, String> message = new ConsumerRecord<>("discord.message.published.v1", 0, 0L, "key", "{}");
        ConsumerRecord<String, String> other = new ConsumerRecord<>("discord.authz.events", 0, 0L, "key", "{}");
        IllegalArgumentException notFound = new IllegalArgumentException("guild not found");

        assertThat(handler.handleOne(notFound, message, mock(), mock())).as("message topic: dead-letter at once").isTrue();
        assertThat(handler.handleOne(notFound, other, mock(), mock())).as("other topic: first retry").isFalse();
        assertThat(handler.handleOne(notFound, other, mock(), mock())).as("other topic: second retry").isFalse();
        assertThat(handler.handleOne(notFound, other, mock(), mock())).as("other topic: recovered after retries").isTrue();
    }

    @Test
    void invalidMessagePublicationRecordIsNotCountedAsRetry() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        var handler = new MessageKafkaConfiguration().messageKafkaErrorHandler(
            kafka, new ObjectMapper(), metrics, "discord", 1000);
        ConsumerRecord<String, String> message = new ConsumerRecord<>("discord.message.published.v1", 0, 0L, "key", "null");

        assertThat(handler.handleOne(new IllegalArgumentException("invalid"), message, mock(), mock())).isTrue();

        assertThat(metrics.find("discord.message.publication.consumer.retries").counter()).isNull();
        assertThat(metrics.get("discord.message.publication.consumer.events").tag("outcome", "dead_letter").counter().count())
            .isEqualTo(1);
    }

    @Test
    void retriesCountOnlyDeliveriesThatAreRetried() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        var handler = new MessageKafkaConfiguration().messageKafkaErrorHandler(
            kafka, new ObjectMapper(), metrics, "discord", 1000);
        ConsumerRecord<String, String> message = new ConsumerRecord<>("discord.message.published.v1", 0, 0L, "key", "{}");
        IllegalStateException outage = new IllegalStateException("database unavailable");

        assertThat(handler.handleOne(outage, message, mock(), mock())).as("first failure: retried").isFalse();
        assertThat(handler.handleOne(outage, message, mock(), mock())).as("second failure: retried").isFalse();
        assertThat(handler.handleOne(outage, message, mock(), mock())).as("third failure: dead-lettered").isTrue();

        assertThat(metrics.get("discord.message.publication.consumer.retries").counter().count()).isEqualTo(2);
        assertThat(metrics.get("discord.message.publication.consumer.events").tag("outcome", "dead_letter").counter().count())
            .isEqualTo(1);
    }

    @Test
    void wrappedInvalidRecordStillSkipsRetries() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        var handler = new MessageKafkaConfiguration().messageKafkaErrorHandler(
            kafka, new ObjectMapper(), metrics, "discord", 1000);
        ConsumerRecord<String, String> message = new ConsumerRecord<>("discord.message.published.v1", 0, 0L, "key", "{}");
        // The error handler only strips listener wrappers, so an IAE further down the cause chain must still match.
        var wrapped = new org.springframework.kafka.listener.ListenerExecutionFailedException(
            "listener failed", new IllegalStateException("projection failed", new IllegalArgumentException("guild not found")));

        assertThat(handler.handleOne(wrapped, message, mock(), mock())).isTrue();
        assertThat(metrics.find("discord.message.publication.consumer.retries").counter()).isNull();
    }

    @Test
    void ignoresRetryMetricsForUnrelatedTopic() {
        KafkaTemplate<String, String> kafka = mock();
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        MessageKafkaConfiguration configuration = new MessageKafkaConfiguration();
        ConsumerRecord<String, String> unrelated = new ConsumerRecord<>("discord.authz.events", 0, 0L, "key", "{}");
        var handler = configuration.messageKafkaErrorHandler(
            kafka, new ObjectMapper(), metrics, "discord", 1000);

        for (int attempt = 0; attempt < 3; attempt++) {
            handler.handleOne(new IllegalStateException("temporary failure"), unrelated, mock(), mock());
        }

        assertThat(metrics.find("discord.message.publication.consumer.retries").counter()).isNull();
    }

    @Test
    void lagPollingUsesItsOwnScheduler() {
        var scheduled = java.util.Arrays.stream(MessagePublicationConsumerLag.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("refresh"))
            .findFirst()
            .orElseThrow()
            .getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertThat(scheduled).isNotNull();
        assertThat(scheduled.scheduler()).isEqualTo("messagePublicationLagScheduler");
    }

    @Test
    void includesTopicPartitionBacklogBeforeFirstCommittedOffset() {
        TopicPartition neverCommitted = new TopicPartition("discord.message.published.v1", 1);

        assertThat(MessagePublicationConsumerLag.calculateLag(
            java.util.Set.of(neverCommitted),
            Map.of(),
            Map.of(neverCommitted, 3L),
            "discord.message.published.v1"))
            .isEqualTo(3L);
    }
}
