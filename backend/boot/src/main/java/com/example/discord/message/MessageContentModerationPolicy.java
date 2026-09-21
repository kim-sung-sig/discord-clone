package com.example.discord.message;

import com.example.discord.moderation.InMemoryModerationService;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class MessageContentModerationPolicy implements MessageContentPolicy {
    private final InMemoryModerationService moderation;

    MessageContentModerationPolicy(InMemoryModerationService moderation) {
        this.moderation = Objects.requireNonNull(moderation, "moderation must not be null");
    }

    @Override
    public void review(
        MessageAuthor author,
        MessageTarget target,
        MessageContent content,
        List<MessageMentionTarget> mentions
    ) {
        if (author instanceof UserMessageAuthor user && target instanceof ChannelMessageTarget channel) {
            var decision = moderation.evaluateMessage(
                channel.guildId(),
                channel.channelId(),
                user.userId(),
                content.value()
            );
            if (decision.blocked()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, decision.reason());
            }
        }
    }
}
