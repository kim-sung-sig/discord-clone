package com.example.discord.message;

public final class MessageNotFoundException extends RuntimeException {
    public MessageNotFoundException() {
        super("message not found");
    }
}
