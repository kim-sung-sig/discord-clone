package com.example.discord.message;

import java.util.UUID;

@FunctionalInterface
public interface MessageLookupPort {
    Message requireMessage(ChannelMessageTarget target, UUID messageId);
}
