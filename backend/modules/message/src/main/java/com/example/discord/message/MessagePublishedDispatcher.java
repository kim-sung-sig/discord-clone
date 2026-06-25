package com.example.discord.message;

@FunctionalInterface
public interface MessagePublishedDispatcher {
    void dispatch(MessagePublished event);
}
