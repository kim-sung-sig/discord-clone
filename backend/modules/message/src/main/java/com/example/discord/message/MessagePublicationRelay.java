package com.example.discord.message;

@FunctionalInterface
public interface MessagePublicationRelay {
    int relay(int limit);
}
