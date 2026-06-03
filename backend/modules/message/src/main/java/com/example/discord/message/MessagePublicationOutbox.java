package com.example.discord.message;

@FunctionalInterface
public interface MessagePublicationOutbox {
    void append(MessagePublished event);
}
