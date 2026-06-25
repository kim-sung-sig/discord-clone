package com.example.discord.message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/messages/outbox")
class MessageOutboxController {
    private static final String INTERNAL_OPERATOR_HEADER = "X-Internal-Message-Outbox-Operator";

    private final MessagePublicationDeadLetterQueue deadLetters;
    private final String operatorToken;

    MessageOutboxController(
        MessagePublicationDeadLetterQueue deadLetters,
        @Value("${discord.message.outbox-operator-token:}") String operatorToken
    ) {
        this.deadLetters = deadLetters;
        this.operatorToken = operatorToken;
    }

    @GetMapping("/dead-letter")
    DeadLetterResponse listDeadLetters(
        @RequestHeader(value = INTERNAL_OPERATOR_HEADER, required = false) String internalOperator,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireInternalOperator(internalOperator);
        return new DeadLetterResponse(
            deadLetters.listDeadLetters(limit).stream()
                .map(DeadLetterEventResponse::from)
                .toList()
        );
    }

    @PostMapping("/dead-letter/{eventId}/replay")
    ResponseEntity<ReplayResponse> replayDeadLetter(
        @RequestHeader(value = INTERNAL_OPERATOR_HEADER, required = false) String internalOperator,
        @PathVariable UUID eventId
    ) {
        requireInternalOperator(internalOperator);
        if (!deadLetters.requeueDeadLetter(eventId, Instant.now())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "message publication dead letter not found");
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new ReplayResponse(eventId, true));
    }

    private void requireInternalOperator(String internalOperator) {
        if (operatorToken == null || operatorToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "message outbox operator disabled");
        }
        if (!secureEquals(operatorToken, internalOperator)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "message outbox operator required");
        }
    }

    private static boolean secureEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }

    record DeadLetterResponse(List<DeadLetterEventResponse> events) {
    }

    record DeadLetterEventResponse(
        UUID eventId,
        UUID messageId,
        MessageAuthorResponse author,
        MessageTargetResponse target,
        List<String> mentions,
        String correlationId,
        Instant occurredAt,
        int attempts,
        String lastError,
        Instant deadLetteredAt
    ) {
        static DeadLetterEventResponse from(DeadLetteredMessagePublication deadLetter) {
            MessagePublished event = deadLetter.event();
            return new DeadLetterEventResponse(
                event.eventId(),
                event.messageId(),
                MessageAuthorResponse.from(event.author()),
                MessageTargetResponse.from(event.target()),
                event.mentions().stream().map(DeadLetterEventResponse::mentionToken).toList(),
                event.correlationId(),
                event.occurredAt(),
                deadLetter.attempts(),
                deadLetter.lastError(),
                deadLetter.deadLetteredAt()
            );
        }

        private static String mentionToken(MessageMentionTarget mention) {
            return switch (mention) {
                case UserMentionTarget user -> user.userId().toString();
                case RoleMentionTarget role -> role.roleId().toString();
                case ChannelMentionTarget channel -> channel.channelId().toString();
                case SpecialMentionTarget special -> special.kind().name().toLowerCase(java.util.Locale.ROOT);
            };
        }
    }

    record MessageAuthorResponse(String type, UUID userId, UUID botId, UUID webhookId) {
        static MessageAuthorResponse from(MessageAuthor author) {
            return switch (author) {
                case UserMessageAuthor user -> new MessageAuthorResponse("USER", user.userId(), null, null);
                case BotMessageAuthor bot -> new MessageAuthorResponse("BOT", null, bot.botId(), null);
                case WebhookMessageAuthor webhook -> new MessageAuthorResponse(
                    "WEBHOOK",
                    null,
                    null,
                    webhook.webhookId()
                );
                case SystemMessageAuthor ignored -> new MessageAuthorResponse("SYSTEM", null, null, null);
            };
        }
    }

    record MessageTargetResponse(
        String type,
        UUID guildId,
        UUID channelId,
        UUID threadId,
        UUID conversationId,
        UUID recipientId
    ) {
        static MessageTargetResponse from(MessageTarget target) {
            return switch (target) {
                case ChannelMessageTarget channel -> new MessageTargetResponse(
                    "CHANNEL",
                    channel.guildId(),
                    channel.channelId(),
                    null,
                    null,
                    null
                );
                case ThreadMessageTarget thread -> new MessageTargetResponse(
                    "THREAD",
                    thread.guildId(),
                    thread.channelId(),
                    thread.threadId(),
                    null,
                    null
                );
                case DirectMessageTarget direct -> new MessageTargetResponse(
                    "DIRECT",
                    null,
                    null,
                    null,
                    direct.conversationId(),
                    direct.recipientId()
                );
            };
        }
    }

    record ReplayResponse(UUID eventId, boolean requeued) {
    }
}
