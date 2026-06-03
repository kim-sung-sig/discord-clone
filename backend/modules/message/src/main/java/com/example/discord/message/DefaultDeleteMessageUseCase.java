package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class DefaultDeleteMessageUseCase implements DeleteMessageUseCase {
    private final MessageMutationGuard mutationGuard;
    private final MessageStore messages;
    private final Clock clock;

    public DefaultDeleteMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageStore messages,
        Clock clock
    ) {
        this.mutationGuard = Objects.requireNonNull(mutationGuard, "mutationGuard must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public DeleteMessageResult delete(DeleteMessageRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Message current = messages.findById(request.messageId()).orElseThrow(MessageNotFoundException::new);
        mutationGuard.requireCanDelete(request.requester(), current);
        Instant now = clock.instant();
        Message deleted = new Message(
            current.id(),
            current.author(),
            current.target(),
            new MessageContent("[deleted]"),
            List.of(),
            current.pinned(),
            true,
            List.of(),
            current.createdAt(),
            now
        );
        return new DeleteMessageResult(messages.save(deleted));
    }
}
