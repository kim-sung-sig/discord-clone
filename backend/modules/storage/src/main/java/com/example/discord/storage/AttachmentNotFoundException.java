package com.example.discord.storage;

public final class AttachmentNotFoundException extends RuntimeException {
    public AttachmentNotFoundException() {
        super("attachment not found");
    }
}
