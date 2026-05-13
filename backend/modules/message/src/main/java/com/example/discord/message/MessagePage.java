package com.example.discord.message;

import java.util.List;

public record MessagePage(List<Message> messages, String nextCursor) {
    public MessagePage {
        messages = List.copyOf(messages);
    }
}
