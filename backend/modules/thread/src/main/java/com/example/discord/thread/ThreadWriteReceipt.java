package com.example.discord.thread;

import java.time.Instant;
import java.util.UUID;

public record ThreadWriteReceipt(UUID threadId, UUID authorId, String content, Instant writtenAt) {
    public ThreadWriteReceipt {
        java.util.Objects.requireNonNull(threadId, "threadId must not be null");
        java.util.Objects.requireNonNull(authorId, "authorId must not be null");
        java.util.Objects.requireNonNull(content, "content must not be null");
        java.util.Objects.requireNonNull(writtenAt, "writtenAt must not be null");
    }
}
