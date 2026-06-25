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

class DefaultEditMessageUseCaseTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void rejectsEditWhenGuardDeniesAccess() {
        Message existing = message(false);
        RecordingMessageStore messages = new RecordingMessageStore(existing);
        MessageMutationRejectedException rejection = new MessageMutationRejectedException("cannot edit message");
        EditMessageUseCase useCase = new DefaultEditMessageUseCase(
            new RejectingMutationGuard(rejection),
            (author, target, content, mentions) -> {
            },
            messages,
            CLOCK
        );

        assertThatThrownBy(() -> useCase.edit(request()))
            .isSameAs(rejection);
    }

    @Test
    void rejectsEditingDeletedMessage() {
        RecordingMessageStore messages = new RecordingMessageStore(message(true));
        EditMessageUseCase useCase = new DefaultEditMessageUseCase(
            new AllowingMutationGuard(),
            (author, target, content, mentions) -> {
            },
            messages,
            CLOCK
        );

        assertThatThrownBy(() -> useCase.edit(request()))
            .isInstanceOf(MessageMutationRejectedException.class)
            .hasMessage("deleted message cannot be edited");
    }

    @Test
    void updatesContentMentionsAndEditHistory() {
        Message existing = message(false);
        RecordingMessageStore messages = new RecordingMessageStore(existing);
        UserMentionTarget mention = new UserMentionTarget(UUID.randomUUID());
        EditMessageUseCase useCase = new DefaultEditMessageUseCase(
            new AllowingMutationGuard(),
            (author, target, content, mentions) -> {
            },
            messages,
            CLOCK
        );

        EditMessageResult result = useCase.edit(new EditMessageRequest(
            existing.id(),
            existing.author(),
            new MessageContent("updated"),
            List.of(mention, mention)
        ));

        assertThat(result.message()).isSameAs(messages.saved);
        assertThat(result.message().content()).isEqualTo(new MessageContent("updated"));
        assertThat(result.message().mentions()).containsExactly(mention);
        assertThat(result.message().editHistory()).containsExactly(new MessageEdit(existing.content(), CLOCK.instant()));
        assertThat(result.message().updatedAt()).isEqualTo(CLOCK.instant());
    }

    private static EditMessageRequest request() {
        Message existing = message(false);
        return new EditMessageRequest(existing.id(), existing.author(), new MessageContent("updated"), List.of());
    }

    private static Message message(boolean deleted) {
        Instant createdAt = Instant.parse("2026-06-03T11:00:00Z");
        return new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            new MessageContent("original"),
            List.of(),
            false,
            deleted,
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
        public Optional<Message> findByIdempotencyKey(
            MessageAuthor author,
            MessageTarget target,
            IdempotencyKey idempotencyKey
        ) {
            return Optional.empty();
        }

        @Override
        public Optional<Message> findById(UUID messageId) {
            return Optional.of(existing);
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
            throw rejection;
        }

        @Override
        public void requireCanDelete(MessageAuthor actor, Message message) {
        }

        @Override
        public void requireCanPin(MessageAuthor actor, Message message) {
        }
    }
}
