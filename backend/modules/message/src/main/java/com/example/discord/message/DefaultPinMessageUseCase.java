package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultPinMessageUseCase implements PinMessageUseCase {
    private static final Logger log = LoggerFactory.getLogger(DefaultPinMessageUseCase.class);

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
        Object requesterId = authorId(request.requester());
        log.info(
            "Pinning message started messageId={} requesterId={} pinned={}",
            request.messageId(),
            requesterId,
            request.pinned()
        );
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
        Message saved = messages.save(updated);
        log.info(
            "Message pin state updated messageId={} requesterId={} guildId={} channelId={} pinned={}",
            saved.id(),
            requesterId,
            saved.guildId(),
            saved.channelId(),
            saved.pinned()
        );
        log.info(
            "Pinning message finished messageId={} requesterId={} pinned={}",
            saved.id(),
            requesterId,
            saved.pinned()
        );
        return new PinMessageResult(saved);
    }

    private static Object authorId(MessageAuthor author) {
        return author instanceof UserMessageAuthor user ? user.userId() : author.getClass().getSimpleName();
    }
}
