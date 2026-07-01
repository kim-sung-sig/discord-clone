package com.example.discord.message;

import java.util.List;

public record MessageReadPage(List<MessageReadModel> messages, String nextCursor) {
    public MessageReadPage {
        messages = List.copyOf(messages);
    }
}
