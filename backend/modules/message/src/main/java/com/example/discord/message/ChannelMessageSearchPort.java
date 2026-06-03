package com.example.discord.message;

import java.util.List;

@FunctionalInterface
public interface ChannelMessageSearchPort {
    List<Message> search(ChannelMessageTarget target, String query, int limit);
}
