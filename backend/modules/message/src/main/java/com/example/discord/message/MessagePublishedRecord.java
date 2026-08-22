package com.example.discord.message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** Versioned, body-free Kafka contract for a published message. */
public record MessagePublishedRecord(
    int schemaVersion,
    UUID eventId,
    UUID messageId,
    UUID guildId,
    UUID channelId,
    String correlationId,
    Instant occurredAt,
    UUID payloadRef,
    String payloadHash
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public MessagePublishedRecord {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported message publication schema version");
        }
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(messageId, "messageId must not be null");
        Objects.requireNonNull(guildId, "guildId must not be null");
        Objects.requireNonNull(channelId, "channelId must not be null");
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(payloadRef, "payloadRef must not be null");
        if (payloadHash == null || payloadHash.isBlank()) {
            throw new IllegalArgumentException("payloadHash must not be blank");
        }
    }

    public static MessagePublishedRecord from(MessagePublished event, String payloadHash) {
        Objects.requireNonNull(event, "event must not be null");
        if (!(event.target() instanceof ChannelMessageTarget channel)) {
            throw new IllegalArgumentException("only channel message publications are supported");
        }
        return new MessagePublishedRecord(
            CURRENT_SCHEMA_VERSION,
            event.eventId(),
            event.messageId(),
            channel.guildId(),
            channel.channelId(),
            event.correlationId(),
            event.occurredAt(),
            event.messageId(),
            payloadHash
        );
    }

    public static String payloadHashFor(MessagePublished event) {
        Objects.requireNonNull(event, "event must not be null");
        String value = event.eventId() + "|" + event.messageId() + "|" + event.target() + "|" + event.occurredAt();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
