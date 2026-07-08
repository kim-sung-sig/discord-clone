package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DefaultEditMessageUseCase implements EditMessageUseCase {
    private static final Logger log = LoggerFactory.getLogger(DefaultEditMessageUseCase.class);

    private final MessageMutationGuard mutationGuard;
    private final MessageContentPolicy contentPolicy;
    private final MessageStore messages;
    private final Clock clock;

    public DefaultEditMessageUseCase(
        MessageMutationGuard mutationGuard,
        MessageContentPolicy contentPolicy,
        MessageStore messages,
        Clock clock
    ) {
        this.mutationGuard = Objects.requireNonNull(mutationGuard, "mutationGuard must not be null");
        this.contentPolicy = Objects.requireNonNull(contentPolicy, "contentPolicy must not be null");
        this.messages = Objects.requireNonNull(messages, "messages must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public EditMessageResult edit(EditMessageRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Object editorId = authorId(request.editor());
        log.info("Editing message started messageId={} editorId={}", request.messageId(), editorId);
        Message current = messages.findById(request.messageId()).orElseThrow(MessageNotFoundException::new);
        mutationGuard.requireCanEdit(request.editor(), current);
        if (current.deleted()) {
            log.warn("Editing message rejected because message is deleted messageId={} editorId={}", current.id(), editorId);
            throw new MessageMutationRejectedException("deleted message cannot be edited");
        }

        List<MessageMentionTarget> mentions = request.mentions().stream().distinct().toList();
        contentPolicy.review(request.editor(), current.target(), request.content(), mentions);
        log.debug(
            "Message edit policy checks passed messageId={} editorId={} mentionCount={}",
            current.id(),
            editorId,
            mentions.size()
        );
        Instant now = clock.instant();
        List<MessageEdit> history = new ArrayList<>(current.editHistory());
        history.add(new MessageEdit(current.content(), now));
        Message updated = new Message(
            current.id(),
            current.author(),
            current.target(),
            request.content(),
            mentions,
            current.pinned(),
            false,
            history,
            current.createdAt(),
            now
        );
        Message saved = messages.save(updated);
        log.info(
            "Message updated messageId={} editorId={} guildId={} channelId={} editHistoryCount={}",
            saved.id(),
            editorId,
            saved.guildId(),
            saved.channelId(),
            saved.editHistory().size()
        );
        log.info("Editing message finished messageId={} editorId={}", saved.id(), editorId);
        return new EditMessageResult(saved);
    }

    private static Object authorId(MessageAuthor author) {
        return author instanceof UserMessageAuthor user ? user.userId() : author.getClass().getSimpleName();
    }
}
