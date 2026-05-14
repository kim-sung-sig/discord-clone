package com.example.discord.thread;

public final class ThreadNotFoundException extends RuntimeException {
    public ThreadNotFoundException() {
        super("thread not found");
    }
}
