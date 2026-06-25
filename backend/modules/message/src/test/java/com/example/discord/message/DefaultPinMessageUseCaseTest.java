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

class DefaultPinMessageUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T12:20:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsPinWhenGuardDeniesAccess() {
        Message existing = message(false);
        MessageMutationRejectedException rejection = new MessageMutationRejectedException("cannot pin message");
        PinMessageUseCase useCase = new DefaultPinMessageUseCase(
            new RejectingMutationGuard(rejection),
            new RecordingMessageStore(existing),
            CLOCK
        );

        assertThatThrownBy(() -> useCase.pin(new PinMessageRequest(existing.id(), existing.author(), true)))
            .isSameAs(rejection);
    }

    @Test
    void changesPinnedState() {
        Message existing = message(false);
        RecordingMessageStore messages = new RecordingMessageStore(existing);
        PinMessageUseCase useCase = new DefaultPinMessageUseCase(
            new AllowingMutationGuard(),
            messages,
            CLOCK
        );

        PinMessageResult result = useCase.pin(new PinMessageRequest(existing.id(), existing.author(), true));

        assertThat(result.message()).isSameAs(messages.saved);
        assertThat(result.message().pinned()).isTrue();
        assertThat(result.message().updatedAt()).isEqualTo(CLOCK.instant());
    }

    private static Message message(boolean pinned) {
        Instant createdAt = Instant.parse("2026-06-03T11:00:00Z");
        return new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            new MessageContent("original"),
            List.of(),
            pinned,
            false,
            List.of(),
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
        }

        @Override
        public void requireCanPin(MessageAuthor actor, Message message) {
            throw rejection;
        }
    }
}
