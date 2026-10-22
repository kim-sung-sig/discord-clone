package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

class MessageKafkaRecoveryFailureTest {
    @Test
    void synchronousDeadLetterSendFailureKeepsRecordAvailableForRecovery() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenThrow(new IllegalStateException("broker unavailable"));

        assertRecordIsRecoveredOnlyAfterAcknowledgement(kafka, false);
    }

    @Test
    void rejectedDeadLetterAcknowledgementKeepsRecordAvailableForRecovery() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker rejected send")));

        assertRecordIsRecoveredOnlyAfterAcknowledgement(kafka, false);
    }

    @Test
    void missingDeadLetterAcknowledgementKeepsRecordAvailableForRecovery() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        assertRecordIsRecoveredOnlyAfterAcknowledgement(kafka, false);
    }

    @Test
    void interruptedDeadLetterAcknowledgementPreservesInterruptAndAllowsLaterRecovery() {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(new CompletableFuture<>());

        try {
            Thread.currentThread().interrupt();
            assertRecordIsRecoveredOnlyAfterAcknowledgement(kafka, true);
        } finally {
            Thread.interrupted();
        }
    }

    private void assertRecordIsRecoveredOnlyAfterAcknowledgement(
        KafkaTemplate<String, String> kafka,
        boolean interrupted
    ) {
        SimpleMeterRegistry metrics = new SimpleMeterRegistry();
        var handler = new MessageKafkaConfiguration().messageKafkaErrorHandler(
            kafka, new ObjectMapper(), metrics, "discord", 1L);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(
            "discord.message.published.v1", 0, 7L, "channel", "{}");
        Consumer<?, ?> consumer = mock();
        MessageListenerContainer container = mock();
        IllegalArgumentException invalidRecord = new IllegalArgumentException("invalid event");

        assertThat(handler.handleOne(invalidRecord, record, consumer, container))
            .as("failed DLQ recovery must leave the source record unhandled")
            .isFalse();
        var deadLetters = metrics.find("discord.message.publication.consumer.events")
            .tag("outcome", "dead_letter").counter();
        assertThat(deadLetters).isNull();
        assertThat(Thread.currentThread().isInterrupted()).isEqualTo(interrupted);
        if (interrupted) {
            Thread.interrupted();
        }

        doReturn(CompletableFuture.completedFuture(mock(SendResult.class)))
            .when(kafka).send(anyString(), anyString(), anyString());

        assertThat(handler.handleOne(invalidRecord, record, consumer, container))
            .as("the same source record is recovered after the broker acknowledges its DLQ record")
            .isTrue();
        assertThat(metrics.get("discord.message.publication.consumer.events")
            .tag("outcome", "dead_letter").counter().count()).isEqualTo(1);
    }
}
