package com.example.discord.message;

@FunctionalInterface
public interface ChannelMessagePagePort {
    MessagePage read(ChannelMessageTarget target, String beforeCursor, int limit);
}
