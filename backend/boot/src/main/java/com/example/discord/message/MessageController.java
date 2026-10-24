package com.example.discord.message;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.GuildService;
import com.example.discord.moderation.AuditLogAction;
import com.example.discord.moderation.ModerationService;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/channels/{channelId}/messages")
class MessageController {
    private final PublishMessageUseCase publishMessage;
    private final EditMessageUseCase editMessage;
    private final DeleteMessageUseCase deleteMessage;
    private final PinMessageUseCase pinMessage;
    private final ChannelMessageQueryService messageQueries;
    private final ChannelMessageReadModelPort readModels;
    private final GuildService guildService;
    private final ModerationService moderationService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final MessageAuthorDirectory authorDirectory;

    MessageController(
        PublishMessageUseCase publishMessage,
        EditMessageUseCase editMessage,
        DeleteMessageUseCase deleteMessage,
        PinMessageUseCase pinMessage,
        ChannelMessageQueryService messageQueries,
        ChannelMessageReadModelPort readModels,
        GuildService guildService,
        ModerationService moderationService,
        AuthenticatedUserResolver authenticatedUserResolver,
        MessageAuthorDirectory authorDirectory
    ) {
        this.publishMessage = publishMessage;
        this.editMessage = editMessage;
        this.deleteMessage = deleteMessage;
        this.pinMessage = pinMessage;
        this.messageQueries = messageQueries;
        this.readModels = readModels;
        this.guildService = guildService;
        this.moderationService = moderationService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.authorDirectory = authorDirectory;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<MessageResponse> create(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody MessageContentRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildIdFor(channelId);
        requireCreateRequest(request);
        String correlationId = MDC.get("request_id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        Message message = publishMessage.publish(new PublishMessageRequest(
            new UserMessageAuthor(requesterId),
            new ChannelMessageTarget(guildId, channelId),
            new MessageContent(request.content()),
            mentionTargetsFrom(request.mentions()),
            new IdempotencyKey(request.idempotencyKey()),
            correlationId
        )).message();
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(
            message,
            readModels.sequenceOf(message.id()),
            authorDisplayName(message)
        ));
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
        return MessagePageResponse.from(page, authorDisplayNames(page.messages()));
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
        return MessageResponse.from(message, readModels.sequenceOf(message.id()), authorDisplayName(message));
    }

    @DeleteMapping("/{messageId}")
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
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
        return MessageResponse.from(message, readModels.sequenceOf(message.id()), authorDisplayName(message));
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
        return MessageResponse.from(message, readModels.sequenceOf(message.id()), authorDisplayName(message));
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
        List<MessageReadModel> messages = messageQueries.search(new ChannelMessageQuery(
            new UserMessageAuthor(requesterId),
            new ChannelMessageTarget(guildId, channelId),
            null,
            limit
        ), q, limit);
        Map<UUID, String> authorNames = authorDisplayNames(messages);
        return messages.stream()
            .map(message -> MessageResponse.from(message, authorNames.get(message.authorId())))
            .toList();
    }

    private String authorDisplayName(Message message) {
        if (message.author() instanceof UserMessageAuthor author) {
            return authorDirectory.displayName(author.userId()).orElse(null);
        }
        return null;
    }

    private Map<UUID, String> authorDisplayNames(List<MessageReadModel> messages) {
        Set<UUID> authorIds = messages.stream().map(MessageReadModel::authorId).collect(Collectors.toSet());
        return authorIds.isEmpty() ? Map.of() : authorDirectory.displayNames(authorIds);
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
        static MessagePageResponse from(MessageReadPage page, Map<UUID, String> authorNames) {
            return new MessagePageResponse(
                page.messages().stream()
                    .map(message -> MessageResponse.from(message, authorNames.get(message.authorId())))
                    .toList(),
                page.nextCursor()
            );
        }
    }

    record MessageResponse(
        UUID id,
        UUID guildId,
        UUID channelId,
        Long sequence,
        UUID authorId,
        String authorDisplayName,
        String content,
        List<String> mentions,
        boolean pinned,
        boolean deleted,
        boolean edited,
        List<MessageEditResponse> editHistory,
        Instant createdAt,
        Instant updatedAt
    ) {
        static MessageResponse from(Message message, long sequence, String authorDisplayName) {
            return new MessageResponse(
                message.id(),
                message.guildId(),
                message.channelId(),
                sequence,
                message.authorId(),
                authorDisplayName,
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

        static MessageResponse from(MessageReadModel message, String authorDisplayName) {
            return new MessageResponse(
                message.id(),
                message.guildId(),
                message.channelId(),
                message.sequence(),
                message.authorId(),
                authorDisplayName,
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

}
