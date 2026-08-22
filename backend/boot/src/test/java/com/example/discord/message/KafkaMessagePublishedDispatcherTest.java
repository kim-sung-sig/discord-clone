package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.discord.message.MessagePublishedRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.support.SendResult;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaMessagePublishedDispatcherTest {
    private final KafkaTemplate<String, String> kafka = mock();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final MessageLookupPort messages = (target, messageId) -> message(messageId, target);
    private final MessagePublishedDispatcher dispatcher = new KafkaMessagePublishedDispatcher(
        kafka,
        mapper,
        messages,
        Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC),
        "discord",
        100
    );

    @Test
    void sendsOriginalEventIdToMessageTopicAndWaitsForBrokerAck() {
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
        MessagePublished event = event();

        dispatcher.dispatch(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(topic.capture(), key.capture(), value.capture());
        assertThat(topic.getValue()).isEqualTo("discord.message.published.v1");
        assertThat(key.getValue()).isEqualTo(((ChannelMessageTarget) event.target()).channelId().toString());
        assertThat(value.getValue()).contains(event.eventId().toString()).doesNotContain("hello");
    }

    @Test
    void brokerFailureIsPropagatedBeforeRelayCanMarkPublished() {
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);

        assertThatThrownBy(() -> dispatcher.dispatch(event()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("message publication Kafka publish failed");
    }

    private static MessagePublished event() {
        return new MessagePublished(
            UUID.randomUUID(),
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            List.of(),
            "correlation",
            Instant.parse("2026-08-22T00:00:00Z")
        );
    }

    private static Message message(UUID messageId, MessageTarget target) {
        return new Message(
            messageId,
            new UserMessageAuthor(UUID.randomUUID()),
            target,
            new MessageContent("hello"),
            List.of(),
            false,
            false,
            List.of(),
            Instant.parse("2026-08-22T00:00:00Z"),
            Instant.parse("2026-08-22T00:00:00Z")
        );
    }
}
