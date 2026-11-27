package com.example.discord.message;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MessageDeliveryPayloadTest {
    @Test
    void createsOrderedPayloadAndOmitsUnknownAuthorDisplayName() {
        UUID mentionedUserId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-12T00:00:00Z");
        Instant updatedAt = createdAt.plusSeconds(12);
        MessageContent content = new MessageContent("edited");
        Message message = new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            content,
            List.of(new UserMentionTarget(mentionedUserId), new SpecialMentionTarget(SpecialMentionKind.EVERYONE)),
            true,
            false,
            List.of(new MessageEdit(new MessageContent("original"), updatedAt)),
            createdAt,
            updatedAt
        );
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("id", message.id().toString());
        expected.put("guildId", message.guildId().toString());
        expected.put("channelId", message.channelId().toString());
        expected.put("authorId", message.authorId().toString());
        expected.put("authorDisplayName", "Author Display");
        expected.put("content", "edited");
        expected.put("mentions", List.of(mentionedUserId.toString(), "everyone"));
        expected.put("pinned", true);
        expected.put("deleted", false);
        expected.put("edited", true);
        expected.put("createdAt", "2026-08-12T00:00:00Z");
        expected.put("updatedAt", "2026-08-12T00:00:12Z");

        assertThat(MessageDeliveryPayload.messageCreate(message, "Author Display")).containsExactlyEntriesOf(expected);

        expected.remove("authorDisplayName");

        assertThat(MessageDeliveryPayload.messageCreate(message, null)).containsExactlyEntriesOf(expected);
    }
}
