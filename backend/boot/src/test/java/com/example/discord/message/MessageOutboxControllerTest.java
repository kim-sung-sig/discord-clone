package com.example.discord.message;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "discord.message.outbox-operator-token=test-outbox-operator")
@AutoConfigureMockMvc
class MessageOutboxControllerTest {
    private static final String INTERNAL_OPERATOR_HEADER = "X-Internal-Message-Outbox-Operator";
    private static final Instant NOW = Instant.parse("2026-06-03T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessagePublicationStore publications;

    @Autowired
    private MessagePublicationOutboxQueue outboxQueue;

    @Autowired
    private MessagePublicationDeadLetterQueue deadLetters;

    @Test
    void rejectsDeadLetterAccessWithoutInternalOperatorHeader() throws Exception {
        mockMvc.perform(get("/api/internal/messages/outbox/dead-letter"))
            .andExpect(status().isForbidden());
    }

    @Test
    void listsAndRequeuesDeadLetterPublicationsWithInternalOperatorHeader() throws Exception {
        MessagePublished event = deadLetterPublication();

        mockMvc.perform(get("/api/internal/messages/outbox/dead-letter")
                .header(INTERNAL_OPERATOR_HEADER, "test-outbox-operator")
                .param("limit", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].eventId").value(event.eventId().toString()))
            .andExpect(jsonPath("$.events[0].messageId").value(event.messageId().toString()))
            .andExpect(jsonPath("$.events[0].target.type").value("CHANNEL"))
            .andExpect(jsonPath("$.events[0].attempts").value(10))
            .andExpect(jsonPath("$.events[0].lastError").value("gateway down 10"))
            .andExpect(jsonPath("$.unpublishedBacklogCount").value(0));

        mockMvc.perform(post("/api/internal/messages/outbox/dead-letter/{eventId}/replay", event.eventId())
                .header(INTERNAL_OPERATOR_HEADER, "test-outbox-operator"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.eventId").value(event.eventId().toString()))
            .andExpect(jsonPath("$.requeued").value(true));

        org.assertj.core.api.Assertions.assertThat(deadLetters.listDeadLetters(10)).isEmpty();
        org.assertj.core.api.Assertions.assertThat(
                outboxQueue.claimPendingPublications(1, NOW.plusSeconds(600), Duration.ofSeconds(30))
            )
            .singleElement()
            .satisfies(publication -> org.assertj.core.api.Assertions.assertThat(publication.event()).isEqualTo(event));
    }

    private MessagePublished deadLetterPublication() {
        Message message = message();
        MessagePublished event = new MessagePublished(
            UUID.randomUUID(),
            message.id(),
            message.author(),
            message.target(),
            message.mentions(),
            "correlation-dead-letter",
            NOW
        );
        publications.savePublished(message, new IdempotencyKey("send-" + UUID.randomUUID()), event);

        for (int attempt = 1; attempt <= 10; attempt++) {
            ClaimedMessagePublication claimed = outboxQueue
                .claimPendingPublications(1, NOW.plusSeconds(attempt * 31L), Duration.ofSeconds(30))
                .getFirst();
            outboxQueue.releaseFailed(
                event.eventId(),
                claimed.claimToken(),
                "gateway down " + attempt,
                NOW.plusSeconds(attempt * 31L),
                Duration.ofSeconds(30)
            );
        }
        return event;
    }

    private static Message message() {
        return new Message(
            UUID.randomUUID(),
            new UserMessageAuthor(UUID.randomUUID()),
            new ChannelMessageTarget(UUID.randomUUID(), UUID.randomUUID()),
            new MessageContent("dead letter me"),
            List.of(new SpecialMentionTarget(SpecialMentionKind.HERE)),
            false,
            false,
            List.of(),
            NOW,
            NOW
        );
    }
}
