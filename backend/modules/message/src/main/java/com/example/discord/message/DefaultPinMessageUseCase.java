package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

public final class DefaultPinMessageUseCase implements PinMessageUseCase {
    private final MessageMutationGuard mutationGuard;
    private final MessageStore messages;
    private final Clock clock;

    public DefaultPinMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageStore messages,
        Clock clock
    ) {
        this.mutationGuard = Objects.requireNonNull(mutationGuard, "mutationGuard must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PinMessageResult pin(PinMessageRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Message current = messages.findById(request.messageId()).orElseThrow(MessageNotFoundException::new);
        mutationGuard.requireCanPin(request.requester(), current);
        Instant now = clock.instant();
        Message updated = new Message(
            current.id(),
            current.author(),
            current.target(),
            current.content(),
            current.mentions(),
            request.pinned(),
            current.deleted(),
            current.editHistory(),
            current.createdAt(),
            now
        );
        return new PinMessageResult(messages.save(updated));
    }
}
