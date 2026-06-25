package com.example.discord.message;

@FunctionalInterface
public interface ChannelMessageReadGuard {
    void requireCanRead(ChannelMessageQuery query);
}
