package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultPublishMessageUseCaseTest {
    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-06-03T11:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsPublishWhenGuardDeniesAccess() {
        MessagePublishRejectedException rejection = new MessagePublishRejectedException("cannot publish message");
        PublishMessageUseCase useCase = new DefaultPublishMessageUseCase(
            (author, target) -> {
                throw rejection;
            },
            (author, target, content, mentions) -> {
            },
            new EmptyMessageStore(),
            FIXED_CLOCK
        );

        assertThatThrownBy(() -> useCase.publish(request()))
            .isSameAs(rejection);
    }

    @Test
    void storesMessageWithAuthorTargetContentAndMentions() {
        RecordingMessageStore messages = new RecordingMessageStore();
        PublishMessageRequest request = request();
        PublishMessageUseCase useCase = new DefaultPublishMessageUseCase(
            (author, target) -> {
            },
            (author, target, content, mentions) -> {
            },
            messages,
            FIXED_CLOCK
        );

        PublishMessageResult result = useCase.publish(request);

        assertThat(result.message()).isSameAs(messages.savedMessage);
        assertThat(result.message().author()).isEqualTo(request.author());
        assertThat(result.message().target()).isEqualTo(request.target());
        assertThat(result.message().content()).isEqualTo(request.content());
        assertThat(result.message().mentions()).isEqualTo(request.mentions());
        assertThat(result.message().createdAt()).isEqualTo(FIXED_CLOCK.instant());
        assertThat(result.message().updatedAt()).isEqualTo(FIXED_CLOCK.instant());
    }

    @Test
    void deduplicatesMentionsBeforePolicyStoreAndEvent() {
        List<MessagePublished> events = new ArrayList<>();
        RecordingMessageStore messages = new RecordingMessageStore(events);
        RecordingContentPolicy contentPolicy = new RecordingContentPolicy();
        UserMentionTarget mention = new UserMentionTarget(UUID.randomUUID());
        PublishMessageRequest request = new PublishMessageRequest(
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            new MessageContent("hello"),
            List.of(mention, mention, new SpecialMentionTarget(SpecialMentionKind.HERE)),
            new IdempotencyKey("send-1"),
            "correlation-1"
        );
        PublishMessageUseCase useCase = new DefaultPublishMessageUseCase(
            (author, target) -> {
            },
            contentPolicy,
            messages,
            FIXED_CLOCK
        );

        PublishMessageResult result = useCase.publish(request);

        assertThat(contentPolicy.reviewedMentions).containsExactly(
            mention,
            new SpecialMentionTarget(SpecialMentionKind.HERE)
        );
        assertThat(result.message().mentions()).containsExactly(
            mention,
            new SpecialMentionTarget(SpecialMentionKind.HERE)
        );
        assertThat(events).singleElement().satisfies(event -> assertThat(event.mentions()).containsExactly(
            mention,
            new SpecialMentionTarget(SpecialMentionKind.HERE)
        ));
    }

    @Test
    void returnsExistingMessageWithoutPublishingEventForIdempotentRetry() {
        PublishMessageRequest request = request();
        Message existing = messageFor(request);
        ExistingMessageStore messages = new ExistingMessageStore(existing);
        PublishMessageUseCase useCase = new DefaultPublishMessageUseCase(
            (author, target) -> {
                throw new AssertionError("guard should not run for idempotent retry");
            },
            (author, target, content, mentions) -> {
                throw new AssertionError("content policy should not run for idempotent retry");
            },
            messages,
            FIXED_CLOCK
        );

        PublishMessageResult result = useCase.publish(request);

        assertThat(result.message()).isSameAs(existing);
    }

    @Test
    void publishesSameContentAgainWhenClientSuppliesDifferentIdempotencyKey() {
        List<MessagePublished> events = new ArrayList<>();
        RecordingMessageStore messages = new RecordingMessageStore(events);
        PublishMessageUseCase useCase = new DefaultPublishMessageUseCase(
            (author, target) -> {
            },
            (author, target, content, mentions) -> {
            },
            messages,
            FIXED_CLOCK
        );
        MessageAuthor author = new UserMessageAuthor(UUID.randomUUID());
        MessageTarget target = new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID());
        MessageContent content = new MessageContent("same text");

        PublishMessageResult first = useCase.publish(new PublishMessageRequest(
            author,
            target,
            content,
            List.of(),
            new IdempotencyKey("send-attempt-1"),
            "correlation-1"
        ));
        PublishMessageResult second = useCase.publish(new PublishMessageRequest(
            author,
            target,
            content,
            List.of(),
            new IdempotencyKey("send-attempt-2"),
            "correlation-2"
        ));

        assertThat(second.message().id()).isNotEqualTo(first.message().id());
        assertThat(events).hasSize(2);
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentContent() {
        PublishMessageRequest request = request();
        Message existing = new Message(
            UUID.randomUUID(),
            request.author(),
            request.target(),
            new MessageContent("different"),
            request.mentions(),
            false,
            false,
            List.of(),
            FIXED_CLOCK.instant(),
            FIXED_CLOCK.instant()
        );
        PublishMessageUseCase useCase = new DefaultPublishMessageUseCase(
            (author, target) -> {
            },
            (author, target, content, mentions) -> {
            },
            new ExistingMessageStore(existing),
            FIXED_CLOCK
        );

        assertThatThrownBy(() -> useCase.publish(request))
            .isInstanceOf(MessagePublishRejectedException.class)
            .hasMessage("idempotency key was reused with a different message payload");
    }

    private static PublishMessageRequest request() {
        return new PublishMessageRequest(
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            new MessageContent("hello"),
            List.of(),
            new IdempotencyKey("send-1"),
            "correlation-1"
        );
    }

    private static Message messageFor(PublishMessageRequest request) {
        return new Message(
            UUID.randomUUID(),
            request.author(),
            request.target(),
            request.content(),
            request.mentions(),
            false,
            false,
            List.of(),
            FIXED_CLOCK.instant(),
            FIXED_CLOCK.instant()
        );
    }

    private static final class EmptyMessageStore implements MessagePublicationStore {
        @Override
        public Optional<Message> findByIdempotencyKey(
            MessageAuthor author,
            MessageTarget target,
            IdempotencyKey idempotencyKey
        ) {
            return Optional.empty();
        }

        @Override
        public Message savePublished(
            Message message,
            IdempotencyKey idempotencyKey,
            MessagePublished event
        ) {
            throw new UnsupportedOperationException("not needed for this test");
        }
    }

    private static final class RecordingMessageStore implements MessagePublicationStore {
        private final List<MessagePublished> events;
        private Message savedMessage;

        private RecordingMessageStore() {
            this(new ArrayList<>());
        }

        private RecordingMessageStore(List<MessagePublished> events) {
            this.events = events;
        }

        @Override
        public Optional<Message> findByIdempotencyKey(
            MessageAuthor author,
            MessageTarget target,
            IdempotencyKey idempotencyKey
        ) {
            return Optional.empty();
        }

        @Override
        public Message savePublished(
            Message message,
            IdempotencyKey idempotencyKey,
            MessagePublished event
        ) {
            this.savedMessage = message;
            this.events.add(event);
            return message;
        }
    }

    private static final class RecordingContentPolicy implements MessageContentPolicy {
        private List<MessageMentionTarget> reviewedMentions;

        @Override
        public void review(
            MessageAuthor author,
            MessageTarget target,
            MessageContent content,
            List<MessageMentionTarget> mentions
        ) {
            this.reviewedMentions = mentions;
        }
    }

    private static final class ExistingMessageStore implements MessagePublicationStore {
        private final Message existing;

        private ExistingMessageStore(Message existing) {
            this.existing = existing;
        }

        @Override
        public Optional<Message> findByIdempotencyKey(
            MessageAuthor author,
            MessageTarget target,
            IdempotencyKey idempotencyKey
        ) {
            return Optional.of(existing);
        }

        @Override
        public Message savePublished(
            Message message,
            IdempotencyKey idempotencyKey,
            MessagePublished event
        ) {
            throw new AssertionError("save should not run for idempotent retry");
        }
    }
}
