package com.example.discord.message;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DefaultEditMessageUseCase implements EditMessageUseCase {
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
        Message current = messages.findById(request.messageId()).orElseThrow(MessageNotFoundException::new);
        mutationGuard.requireCanEdit(request.editor(), current);
        if (current.deleted()) {
            throw new MessageMutationRejectedException("deleted message cannot be edited");
        }

        List<MessageMentionTarget> mentions = request.mentions().stream().distinct().toList();
        contentPolicy.review(request.editor(), current.target(), request.content(), mentions);
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
        return new EditMessageResult(messages.save(updated));
    }
}
