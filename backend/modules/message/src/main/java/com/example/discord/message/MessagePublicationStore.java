package com.example.discord.message;

import java.util.Optional;

public interface MessagePublicationStore {
    Optional<Message> findByIdempotencyKey(
        MessageAuthor author,
        MessageTarget target,
        IdempotencyKey idempotencyKey
    );

    Message savePublished(
        Message message,
        IdempotencyKey idempotencyKey,
        MessagePublished event
    );
}
