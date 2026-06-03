package com.example.discord.message;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface MessagePublicationOutboxQueue {
    List<ClaimedMessagePublication> claimPendingPublications(int limit, Instant claimedAt, Duration lease);

    void markPublished(UUID eventId, UUID claimToken, Instant publishedAt);

    void releaseFailed(UUID eventId, UUID claimToken, String errorMessage, Instant failedAt);
}
