package com.example.discord.message;

import java.util.UUID;

interface MessagePublicationInbox {
    boolean claim(UUID eventId);

    void release(UUID eventId);
}
