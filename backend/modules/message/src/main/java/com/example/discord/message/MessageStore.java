package com.example.discord.message;

import java.util.Optional;
import java.util.UUID;

public interface MessageStore {
    Optional<Message> findById(UUID messageId);

    Optional<Message> findByIdempotencyKey(
        MessageAuthor author,
        MessageTarget target,
        IdempotencyKey idempotencyKey
    );

    Message save(Message message);

    Message save(Message message, IdempotencyKey idempotencyKey);
}
