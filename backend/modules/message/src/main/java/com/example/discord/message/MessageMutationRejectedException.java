package com.example.discord.message;

public class MessageMutationRejectedException extends RuntimeException {
    public MessageMutationRejectedException(String message) {
        super(message);
    }
}
