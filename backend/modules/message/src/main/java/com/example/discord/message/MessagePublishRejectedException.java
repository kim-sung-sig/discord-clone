package com.example.discord.message;

public class MessagePublishRejectedException extends RuntimeException {
    public MessagePublishRejectedException(String message) {
        super(message);
    }
}
