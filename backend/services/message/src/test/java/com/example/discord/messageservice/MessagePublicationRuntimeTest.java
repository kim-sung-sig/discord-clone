package com.example.discord.messageservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.ClaimedMessagePublication;
import com.example.discord.message.DefaultMessagePublicationRelay;
import com.example.discord.message.MessagePublished;
import com.example.discord.message.MessagePublishedDispatcher;
import com.example.discord.message.MessagePublicationOutboxQueue;
import com.example.discord.message.MessagePublicationRelay;
import com.example.discord.message.UserMessageAuthor;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class MessagePublicationRuntimeTest {
    @Test
    void serviceRelayLeavesOutboxUnpublishedWhenBrokerAckFails() {
        RecordingQueue queue = new RecordingQueue(event());
        KafkaTemplate<String, String> kafka = mock();
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);
        MessagePublicationRelay dispatcherRelay = new DefaultMessagePublicationRelay(
            queue,
            new KafkaMessagePublishedDispatcher(
                kafka,
                new ObjectMapper().findAndRegisterModules(),
                Clock.systemUTC(),
                "discord",
                100
            ),
            Clock.systemUTC(),
            Duration.ofSeconds(30),
            Duration.ofSeconds(1)
        );

        assertThatThrownBy(() -> dispatcherRelay.relay(1)).isInstanceOf(IllegalStateException.class);
        assertThat(queue.marked).isEmpty();
        assertThat(queue.released).containsExactly(queue.event.eventId());
    }

    @Test
    void postgresKafkaContextRegistersRelayAndWorker() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("postgres", "kafka");
            context.registerBean(KafkaTemplate.class, () -> mock(KafkaTemplate.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
            context.registerBean(com.example.discord.message.MessagePublicationOutboxQueue.class, () -> mock());
            context.register(MessagePublicationRuntimeConfiguration.class);
            context.refresh();

            assertThat(context.getBean(com.example.discord.message.MessagePublicationRelay.class)).isNotNull();
            assertThat(context.getBean(MessagePublicationRuntimeConfiguration.MessagePublicationRelayWorker.class))
                .isNotNull();
        }
    }

    @Test
    void serviceDispatcherUsesMessageTopicAndOriginalEventId() throws Exception {
        KafkaTemplate<String, String> kafka = mock();
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture((SendResult<String, String>) null));
        MessagePublished event = event();
        MessagePublishedDispatcher dispatcher = new KafkaMessagePublishedDispatcher(
            kafka,
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(Instant.parse("2026-08-22T00:00:00Z"), ZoneOffset.UTC),
            "discord",
            100
        );

        dispatcher.dispatch(event);

        ArgumentCaptor<String> topic = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> value = ArgumentCaptor.forClass(String.class);
        verify(kafka).send(topic.capture(), key.capture(), value.capture());
        assertThat(topic.getValue()).isEqualTo("discord.message.published.v1");
        assertThat(key.getValue()).isEqualTo(((ChannelMessageTarget) event.target()).channelId().toString());
        assertThat(value.getValue()).contains(event.eventId().toString());
    }

    @Test
    void serviceDispatcherFailureIsVisibleToRelay() {
        KafkaTemplate<String, String> kafka = mock();
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker unavailable"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);
        MessagePublishedDispatcher dispatcher = new KafkaMessagePublishedDispatcher(
            kafka,
            new ObjectMapper().findAndRegisterModules(),
            Clock.systemUTC(),
            "discord",
            100
        );

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

    private static final class RecordingQueue implements MessagePublicationOutboxQueue {
        private final MessagePublished event;
        private final UUID claimToken = UUID.randomUUID();
        private final List<UUID> marked = new ArrayList<>();
        private final List<UUID> released = new ArrayList<>();

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
            released.add(eventId);
        }

        @Override
        public long unpublishedBacklogCount() {
            return marked.isEmpty() ? 1 : 0;
        }
    }
}
