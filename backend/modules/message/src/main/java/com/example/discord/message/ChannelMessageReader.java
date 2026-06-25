package com.example.discord.message;

public interface ChannelMessageReader {
    MessagePage read(ChannelMessageQuery query);
}
