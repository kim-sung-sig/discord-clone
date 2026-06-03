package com.example.discord.message;

@FunctionalInterface
public interface MessagePublishGuard {
    void requireCanPublish(MessageAuthor author, MessageTarget target);
}
