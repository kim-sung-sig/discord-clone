package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultPublishMessageUseCase implements PublishMessageUseCase {
    private static final Logger log = LoggerFactory.getLogger(DefaultPublishMessageUseCase.class);

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
        log.info(
            "Publishing message started authorId={} guildId={} channelId={} mentionCount={} correlationId={}",
            authorId(request.author()),
            guildId(request.target()),
            channelId(request.target()),
            mentions.size(),
            request.correlationId()
        );

        Optional<Message> existing = publications.findByIdempotencyKey(
            request.author(),
            request.target(),
            request.idempotencyKey()
        );
        if (existing.isPresent()) {
            Message previous = existing.get();
            requireSamePayload(previous, request.content(), mentions);
            log.info(
                "Publishing message finished with idempotent replay messageId={} authorId={} guildId={} channelId={}",
                previous.id(),
                previous.authorId(),
                previous.guildId(),
                previous.channelId()
            );
            return new PublishMessageResult(previous);
        }

        publishGuard.requireCanPublish(request.author(), request.target());
        contentPolicy.review(request.author(), request.target(), request.content(), mentions);
        log.debug(
            "Message publish policy checks passed authorId={} guildId={} channelId={} mentionCount={}",
            authorId(request.author()),
            guildId(request.target()),
            channelId(request.target()),
            mentions.size()
        );

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
        log.info(
            "Message persisted and outbox event created messageId={} eventId={} authorId={} guildId={} channelId={}",
            saved.id(),
            event.eventId(),
            saved.authorId(),
            saved.guildId(),
            saved.channelId()
        );
        log.info(
            "Publishing message finished messageId={} authorId={} guildId={} channelId={}",
            saved.id(),
            saved.authorId(),
            saved.guildId(),
            saved.channelId()
        );
        return new PublishMessageResult(saved);
    }

    private static Object authorId(MessageAuthor author) {
        return author instanceof UserMessageAuthor user ? user.userId() : author.getClass().getSimpleName();
    }

    private static Object guildId(MessageTarget target) {
        if (target instanceof ChannelMessageTarget channel) {
            return channel.guildId();
        }
        if (target instanceof ThreadMessageTarget thread) {
            return thread.guildId();
        }
        return target.getClass().getSimpleName();
    }

    private static Object channelId(MessageTarget target) {
        if (target instanceof ChannelMessageTarget channel) {
            return channel.channelId();
        }
        if (target instanceof ThreadMessageTarget thread) {
            return thread.channelId();
        }
        return target.getClass().getSimpleName();
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
