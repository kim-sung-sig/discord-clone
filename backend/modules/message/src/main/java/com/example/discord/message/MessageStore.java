package com.example.discord.message;

import java.util.Optional;

public interface MessageStore {
    Optional<Message> findByIdempotencyKey(
        MessageAuthor author,
        MessageTarget target,
        IdempotencyKey idempotencyKey
    );

    Message save(Message message, IdempotencyKey idempotencyKey);
}
