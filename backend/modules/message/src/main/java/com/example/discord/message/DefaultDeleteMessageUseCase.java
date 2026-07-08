package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultDeleteMessageUseCase implements DeleteMessageUseCase {
    private static final Logger log = LoggerFactory.getLogger(DefaultDeleteMessageUseCase.class);

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
        Object requesterId = authorId(request.requester());
        log.info("Deleting message started messageId={} requesterId={}", request.messageId(), requesterId);
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
        Message saved = messages.save(deleted);
        log.info(
            "Message soft-deleted messageId={} requesterId={} guildId={} channelId={}",
            saved.id(),
            requesterId,
            saved.guildId(),
            saved.channelId()
        );
        log.info("Deleting message finished messageId={} requesterId={}", saved.id(), requesterId);
        return new DeleteMessageResult(saved);
    }

    private static Object authorId(MessageAuthor author) {
        return author instanceof UserMessageAuthor user ? user.userId() : author.getClass().getSimpleName();
    }
}
