package com.example.discord.message;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.moderation.AuditLogAction;
import com.example.discord.moderation.InMemoryModerationService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
class MessageController {
    private final PublishMessageUseCase publishMessage;
    private final EditMessageUseCase editMessage;
    private final DeleteMessageUseCase deleteMessage;
    private final PinMessageUseCase pinMessage;
    private final ChannelMessageQueryService messageQueries;
    private final InMemoryGuildService guildService;
    private final InMemoryModerationService moderationService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    MessageController(
        PublishMessageUseCase publishMessage,
        EditMessageUseCase editMessage,
        DeleteMessageUseCase deleteMessage,
        PinMessageUseCase pinMessage,
        ChannelMessageQueryService messageQueries,
        InMemoryGuildService guildService,
        InMemoryModerationService moderationService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.publishMessage = publishMessage;
        this.editMessage = editMessage;
        this.deleteMessage = deleteMessage;
        this.pinMessage = pinMessage;
        this.messageQueries = messageQueries;
        this.guildService = guildService;
        this.moderationService = moderationService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping
    ResponseEntity<MessageResponse> create(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody MessageContentRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildIdFor(channelId);
        requireCreateRequest(request);
        Message message = publishMessage.publish(new PublishMessageRequest(
            new UserMessageAuthor(requesterId),
            new ChannelMessageTarget(guildId, channelId),
            new MessageContent(request.content()),
            mentionTargetsFrom(request.mentions()),
            new IdempotencyKey(request.idempotencyKey()),
            UUID.randomUUID().toString()
        )).message();
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(message));
    }

    @GetMapping
    MessagePageResponse list(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(required = false) String before,
        @RequestParam(defaultValue = "50") int limit
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildIdFor(channelId);
        MessageReadPage page = messageQueries.read(new ChannelMessageQuery(
            new UserMessageAuthor(requesterId),
            new ChannelMessageTarget(guildId, channelId),
            before,
            limit
        ));
        return MessagePageResponse.from(page);
    }

    @PatchMapping("/{messageId}")
    MessageResponse edit(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody MessageContentRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        Message message = editMessage.edit(new EditMessageRequest(
            messageId,
            new UserMessageAuthor(requesterId),
            new MessageContent(request.content()),
            mentionTargetsFrom(request.mentions())
        )).message();
        return MessageResponse.from(message);
    }

    @DeleteMapping("/{messageId}")
    ResponseEntity<Void> delete(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        Message message = deleteMessage.delete(new DeleteMessageRequest(
            messageId,
            new UserMessageAuthor(requesterId)
        )).message();
        moderationService.appendAudit(message.guildId(), AuditLogAction.MESSAGE_DELETED, requesterId, messageId, "message deleted");
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{messageId}/pin")
    MessageResponse pin(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        Message message = pinMessage.pin(new PinMessageRequest(
            messageId,
            new UserMessageAuthor(requesterId),
            true
        )).message();
        moderationService.appendAudit(message.guildId(), AuditLogAction.MESSAGE_PINNED, requesterId, messageId, "message pinned");
        return MessageResponse.from(message);
    }

    @DeleteMapping("/{messageId}/pin")
    MessageResponse unpin(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        Message message = pinMessage.pin(new PinMessageRequest(
            messageId,
            new UserMessageAuthor(requesterId),
            false
        )).message();
        moderationService.appendAudit(message.guildId(), AuditLogAction.MESSAGE_UNPINNED, requesterId, messageId, "message unpinned");
        return MessageResponse.from(message);
    }

    @GetMapping("/search")
    List<MessageResponse> search(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam String q,
        @RequestParam(defaultValue = "50") int limit
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildIdFor(channelId);
        return messageQueries.search(new ChannelMessageQuery(
                new UserMessageAuthor(requesterId),
                new ChannelMessageTarget(guildId, channelId),
                null,
                limit
            ), q, limit).stream()
            .map(MessageResponse::from)
            .toList();
    }

    private UUID guildIdFor(UUID channelId) {
        return guildService.guildIdForChannel(channelId);
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    private static void requireCreateRequest(MessageContentRequest request) {
        requireRequest(request);
        if (request.content() == null || request.idempotencyKey() == null) {
            throw new IllegalArgumentException("content and idempotencyKey are required");
        }
    }

    private static List<MessageMentionTarget> mentionTargetsFrom(List<MessageMentionRequest> mentions) {
        if (mentions == null) {
            return List.of();
        }
        return mentions.stream().map(MessageController::mentionTargetFrom).toList();
    }

    private static MessageMentionTarget mentionTargetFrom(MessageMentionRequest mention) {
        if (mention == null || mention.type() == null) {
            throw new IllegalArgumentException("mention type is required");
        }
        return switch (mention.type().trim().toUpperCase(Locale.ROOT)) {
            case "USER" -> new UserMentionTarget(requiredMentionId(mention));
            case "ROLE" -> new RoleMentionTarget(requiredMentionId(mention));
            case "CHANNEL" -> new ChannelMentionTarget(requiredMentionId(mention));
            case "EVERYONE" -> new SpecialMentionTarget(SpecialMentionKind.EVERYONE);
            case "HERE" -> new SpecialMentionTarget(SpecialMentionKind.HERE);
            default -> throw new IllegalArgumentException("unsupported mention type");
        };
    }

    private static UUID requiredMentionId(MessageMentionRequest mention) {
        if (mention.id() == null) {
            throw new IllegalArgumentException("mention id is required");
        }
        return mention.id();
    }

    record MessageContentRequest(String content, String idempotencyKey, List<MessageMentionRequest> mentions) {
    }

    record MessageMentionRequest(String type, UUID id) {
    }

    record MessagePageResponse(List<MessageResponse> messages, String nextCursor) {
        static MessagePageResponse from(MessageReadPage page) {
            return new MessagePageResponse(
                page.messages().stream().map(MessageResponse::from).toList(),
                page.nextCursor()
            );
        }
    }

    record MessageResponse(
        UUID id,
        UUID guildId,
        UUID channelId,
        UUID authorId,
        String content,
        List<String> mentions,
        boolean pinned,
        boolean deleted,
        boolean edited,
        List<MessageEditResponse> editHistory,
        Instant createdAt,
        Instant updatedAt
    ) {
        static MessageResponse from(Message message) {
            return new MessageResponse(
                message.id(),
                message.guildId(),
                message.channelId(),
                message.authorId(),
                message.content().value(),
                message.mentions().stream().map(MessageResponse::mentionToken).toList(),
                message.pinned(),
                message.deleted(),
                message.edited(),
                message.editHistory().stream().map(MessageEditResponse::from).toList(),
                message.createdAt(),
                message.updatedAt()
            );
        }

        static MessageResponse from(MessageReadModel message) {
            return new MessageResponse(
                message.id(),
                message.guildId(),
                message.channelId(),
                message.authorId(),
                message.content(),
                message.mentions(),
                message.pinned(),
                message.deleted(),
                message.edited(),
                List.of(),
                message.createdAt(),
                message.updatedAt()
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

    record MessageEditResponse(String content, Instant editedAt) {
        static MessageEditResponse from(MessageEdit edit) {
            return new MessageEditResponse(edit.content().value(), edit.editedAt());
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = MessageController.class)
class MessageControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<MessageController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new MessageController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<MessageController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new MessageController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(MessageNotFoundException.class)
    ResponseEntity<MessageController.ErrorResponse> missingMessage(MessageNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MessagePublishRejectedException.class)
    ResponseEntity<MessageController.ErrorResponse> rejectedPublish(MessagePublishRejectedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MessageMutationRejectedException.class)
    ResponseEntity<MessageController.ErrorResponse> rejectedMutation(MessageMutationRejectedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageController.ErrorResponse(exception.getMessage()));
    }
}
