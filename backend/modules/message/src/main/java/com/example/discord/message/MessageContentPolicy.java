package com.example.discord.message;

import java.util.List;

@FunctionalInterface
public interface MessageContentPolicy {
    void review(
        MessageAuthor author,
        MessageTarget target,
        MessageContent content,
        List<MessageMentionTarget> mentions
    );
}
