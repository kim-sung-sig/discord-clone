package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaGatewayEventBusTest {
    private final KafkaTemplate<String, String> kafka = mock();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-05-20T00:00:00Z"), ZoneOffset.UTC);
    private final KafkaGatewayEventBus eventBus = new KafkaGatewayEventBus(
        kafka,
        new ObjectMapper(),
        meterRegistry,
        clock,
        "node-a",
        "discord",
        1
    );

    @Test
    void publishWritesSanitizedGatewayEventToKafkaTopic() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();

        GatewayBusEvent event = eventBus.publish(new GatewayBusPublishCommand(
            "MESSAGE_CREATE",
            guildId,
            channelId,
            Map.of(
                "content", "safe",
                "accessToken", "must-not-leak",
                "attachment", Map.of("signedUrl", "https://cdn.example.test/file?X-Amz-Signature=secret")
            )
        ));

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(topic.capture(), key.capture(), value.capture());
        assertThat(topic.getValue()).isEqualTo("discord.gateway.events");
        assertThat(key.getValue()).isEqualTo(guildId.toString());
        assertThat(value.getValue())
            .contains(event.eventId())
            .contains("safe")
            .contains("node-a")
            .doesNotContain("must-not-leak")
            .doesNotContain("signedUrl")
            .doesNotContain("X-Amz-Signature");
    }

    @Test
    void consumeRemoteKafkaEventNotifiesListenersOnce() throws Exception {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        GatewayBusEvent published = eventBus.publish(new GatewayBusPublishCommand(
            "MESSAGE_CREATE",
            guildId,
            channelId,
            Map.of("content", "safe")
        ));
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(anyString(), anyString(), value.capture());
        String remoteEnvelope = value.getValue().replace("\"sourceNodeId\":\"node-a\"", "\"sourceNodeId\":\"node-b\"");
        ArrayList<GatewayBusEvent> received = new ArrayList<>();
        eventBus.addEventListener(received::add);

        eventBus.handleMessage(remoteEnvelope);
        eventBus.handleMessage(value.getValue());

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).usingRecursiveComparison()
            .ignoringFields("eventId")
            .isEqualTo(published);
        assertThat(received.get(0).eventId()).isEqualTo(published.eventId());
    }

    @Test
    void consumeRemoteGuildWideKafkaEventPreservesNullChannelId() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        GatewayBusEvent published = eventBus.publish(new GatewayBusPublishCommand(
            "GUILD_UPDATE",
            UUID.randomUUID(),
            null,
            Map.of("name", "renamed")
        ));
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(anyString(), anyString(), value.capture());
        String remoteEnvelope = value.getValue().replace("\"sourceNodeId\":\"node-a\"", "\"sourceNodeId\":\"node-b\"");
        ArrayList<GatewayBusEvent> received = new ArrayList<>();
        eventBus.addEventListener(received::add);

        eventBus.handleMessage(remoteEnvelope);

        assertThat(received).hasSize(1);
        assertThat(received.get(0)).usingRecursiveComparison().isEqualTo(published);
        assertThat(received.get(0).channelId()).isNull();
    }

    @Test
    void malformedConsumerMessageIsDeadLetteredWithoutRawPayloadLeak() {
        ArrayList<GatewayBusEvent> received = new ArrayList<>();
        eventBus.addEventListener(received::add);

        eventBus.handleMessage("{\"accessToken\":\"secret-token\"");

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(topic.capture(), key.capture(), value.capture());
        assertThat(topic.getValue()).isEqualTo("discord.gateway.events.dead-letter");
        assertThat(key.getValue()).isEqualTo("node-a");
        assertThat(value.getValue())
            .contains("MALFORMED_MESSAGE")
            .contains("messageSha256Prefix")
            .doesNotContain("secret-token")
            .doesNotContain("accessToken");
        assertThat(eventBus.deadLetterMetrics().total()).isEqualTo(1);
        assertThat(eventBus.deadLetterMetrics().byReason()).containsEntry("MALFORMED_MESSAGE", 1L);
        assertThat(eventBus.deadLetterMetrics().alert().active()).isTrue();
        assertThat(eventBus.deadLetterMetrics().alert().reason())
            .contains("DLQ count 1 reached threshold 1")
            .doesNotContain("secret-token");
        assertThat(meterRegistry.counter("discord.gateway.kafka.dlq.records", "reason", "MALFORMED_MESSAGE").count())
            .isEqualTo(1.0);
        assertThat(received).isEmpty();
    }

    @Test
    void listenerFailureIsDeadLetteredWithoutPayloadLeak() {
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(CompletableFuture.completedFuture(null));
        GatewayBusEvent published = eventBus.publish(new GatewayBusPublishCommand(
            "MESSAGE_CREATE",
            UUID.randomUUID(),
            UUID.randomUUID(),
            Map.of("content", "should-not-enter-dlq")
        ));
        ArgumentCaptor<String> publishedValue = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(anyString(), anyString(), publishedValue.capture());
        String remoteEnvelope = publishedValue.getValue().replace("\"sourceNodeId\":\"node-a\"", "\"sourceNodeId\":\"node-b\"");
        reset(kafka);
        eventBus.addEventListener(event -> {
            throw new IllegalStateException("listener failure with secret-token");
        });

        eventBus.handleMessage(remoteEnvelope);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(topic.capture(), anyString(), value.capture());
        assertThat(topic.getValue()).isEqualTo("discord.gateway.events.dead-letter");
        assertThat(value.getValue())
            .contains("LISTENER_FAILURE")
            .contains(published.eventId())
            .doesNotContain("should-not-enter-dlq")
            .doesNotContain("secret-token")
            .doesNotContain("listener failure");
        assertThat(eventBus.deadLetterMetrics().total()).isEqualTo(1);
        assertThat(eventBus.deadLetterMetrics().byReason()).containsEntry("LISTENER_FAILURE", 1L);
        assertThat(eventBus.deadLetterMetrics().alert().active()).isTrue();
    }
}
