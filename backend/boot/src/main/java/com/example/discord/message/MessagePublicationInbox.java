package com.example.discord.message;

import java.util.UUID;

interface MessagePublicationInbox {
    boolean isProcessed(UUID eventId);

    void markProcessed(UUID eventId);
}
