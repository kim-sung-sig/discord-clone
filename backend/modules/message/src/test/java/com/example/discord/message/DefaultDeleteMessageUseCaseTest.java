package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DefaultDeleteMessageUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T12:10:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsDeleteWhenGuardDeniesAccess() {
        Message existing = message();
        MessageMutationRejectedException rejection = new MessageMutationRejectedException("cannot delete message");
        DeleteMessageUseCase useCase = new DefaultDeleteMessageUseCase(
            new RejectingMutationGuard(rejection),
            new RecordingMessageStore(existing),
            CLOCK
        );

        assertThatThrownBy(() -> useCase.delete(new DeleteMessageRequest(existing.id(), existing.author())))
            .isSameAs(rejection);
    }

    @Test
    void marksMessageDeletedAndClearsSensitiveState() {
        Message existing = message();
        RecordingMessageStore messages = new RecordingMessageStore(existing);
        DeleteMessageUseCase useCase = new DefaultDeleteMessageUseCase(
            new AllowingMutationGuard(),
            messages,
            CLOCK
        );

        DeleteMessageResult result = useCase.delete(new DeleteMessageRequest(existing.id(), existing.author()));

        assertThat(result.message()).isSameAs(messages.saved);
        assertThat(result.message().deleted()).isTrue();
        assertThat(result.message().content()).isEqualTo(new MessageContent("[deleted]"));
        assertThat(result.message().mentions()).isEmpty();
        assertThat(result.message().editHistory()).isEmpty();
        assertThat(result.message().updatedAt()).isEqualTo(CLOCK.instant());
    }

    private static Message message() {
        Instant createdAt = Instant.parse("2026-06-03T11:00:00Z");
        MessageContent original = new MessageContent("original");
        return new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            original,
            List.of(new SpecialMentionTarget(SpecialMentionKind.HERE)),
            false,
            false,
            List.of(new MessageEdit(original, createdAt.plusSeconds(10))),
            createdAt,
            createdAt
        );
    }

    private static final class RecordingMessageStore implements MessageStore {
        private final Message existing;
        private Message saved;

        private RecordingMessageStore(Message existing) {
            this.existing = existing;
        }

        @Override
        public Optional<Message> findById(UUID messageId) {
            return Optional.of(existing);
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
        public Message save(Message message) {
            this.saved = message;
            return message;
        }

        @Override
        public Message save(Message message, IdempotencyKey idempotencyKey) {
            return save(message);
        }
    }

    private record AllowingMutationGuard() implements MessageMutationGuard {
        @Override
        public void requireCanEdit(MessageAuthor actor, Message message) {
        }

        @Override
        public void requireCanDelete(MessageAuthor actor, Message message) {
        }

        @Override
        public void requireCanPin(MessageAuthor actor, Message message) {
        }
    }

    private record RejectingMutationGuard(MessageMutationRejectedException rejection) implements MessageMutationGuard {
        @Override
        public void requireCanEdit(MessageAuthor actor, Message message) {
        }

        @Override
        public void requireCanDelete(MessageAuthor actor, Message message) {
            throw rejection;
        }

        @Override
        public void requireCanPin(MessageAuthor actor, Message message) {
        }
    }
}
