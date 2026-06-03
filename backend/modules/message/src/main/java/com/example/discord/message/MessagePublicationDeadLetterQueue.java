package com.example.discord.message;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessagePublicationDeadLetterQueue {
    List<DeadLetteredMessagePublication> listDeadLetters(int limit);

    boolean requeueDeadLetter(UUID eventId, Instant requestedAt);
}
