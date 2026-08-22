package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessagePublishedRecordTest {
    @Test
    void createsVersionedRecordWithoutChangingOriginalEventId() {
        UUID eventId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID guildId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        MessagePublished event = new MessagePublished(
            eventId,
            messageId,
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(guildId, channelId),
            List.of(),
            "correlation",
            Instant.parse("2026-08-22T00:00:00Z")
        );

        MessagePublishedRecord record = MessagePublishedRecord.from(event, "payload-hash");

        assertThat(record.schemaVersion()).isEqualTo(1);
        assertThat(record.eventId()).isEqualTo(eventId);
        assertThat(record.messageId()).isEqualTo(messageId);
        assertThat(record.guildId()).isEqualTo(guildId);
        assertThat(record.channelId()).isEqualTo(channelId);
        assertThat(record.payloadRef()).isEqualTo(messageId);
        assertThat(record.payloadHash()).isEqualTo("payload-hash");
    }
}
