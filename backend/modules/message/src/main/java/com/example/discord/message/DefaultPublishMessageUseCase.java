package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class DefaultPublishMessageUseCase implements PublishMessageUseCase {
    private final MessagePublishGuard publishGuard;
    private final MessageContentPolicy contentPolicy;
    private final MessagePublicationStore publications;
    private final Clock clock;

    public DefaultPublishMessageUseCase(
        MessagePublishGuard publishGuard,
        MessageContentPolicy contentPolicy,
        MessagePublicationStore publications,
        Clock clock
    ) {
        this.publishGuard = Objects.requireNonNull(publishGuard, "publishGuard must not be null");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy must not be null");
        this.publications = Objects.requireNonNull(publications, "publications must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public PublishMessageResult publish(PublishMessageRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        List<MessageMentionTarget> mentions = distinctMentions(request.mentions());

        Optional<Message> existing = publications.findByIdempotencyKey(
            request.author(),
            request.target(),
            request.idempotencyKey()
        );
        if (existing.isPresent()) {
            Message previous = existing.get();
            requireSamePayload(previous, request.content(), mentions);
            return new PublishMessageResult(previous);
        }

        publishGuard.requireCanPublish(request.author(), request.target());
        contentPolicy.review(request.author(), request.target(), request.content(), mentions);

        Instant now = clock.instant();
        Message message = new Message(
            UUID.randomUUID(),
            request.author(),
            request.target(),
            request.content(),
            mentions,
            false,
            false,
            List.of(),
            now,
            now
        );
        MessagePublished event = new MessagePublished(
            UUID.randomUUID(),
            message.id(),
            message.author(),
            message.target(),
            message.mentions(),
            request.correlationId(),
            now
        );
        Message saved = publications.savePublished(message, request.idempotencyKey(), event);
        return new PublishMessageResult(saved);
    }

    private static List<MessageMentionTarget> distinctMentions(List<MessageMentionTarget> mentions) {
        return mentions.stream().distinct().toList();
    }

    private static void requireSamePayload(
        Message previous,
        MessageContent content,
        List<MessageMentionTarget> mentions
    ) {
        if (!previous.content().equals(content) || !previous.mentions().equals(mentions)) {
            throw new MessagePublishRejectedException("idempotency key was reused with a different message payload");
        }
    }
}
