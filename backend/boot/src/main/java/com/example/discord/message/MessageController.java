package com.example.discord.message;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import java.time.Instant;
import java.util.List;
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
    private final InMemoryMessageService messageService;
    private final InMemoryGuildService guildService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    MessageController(
        InMemoryMessageService messageService,
        InMemoryGuildService guildService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.messageService = messageService;
        this.guildService = guildService;
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
        if (!guildService.canSendMessages(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
        }
        requireRequest(request);
        Message message = messageService.create(new CreateMessageCommand(guildId, channelId, requesterId, request.content()));
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
        UUID guildId = requireView(channelId, requesterId);
        MessagePage page = messageService.messages(guildId, channelId, before, limit);
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
        UUID guildId = guildIdFor(channelId);
        if (!guildService.canSendMessages(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "send messages permission required");
        }
        Message message = messageService.message(guildId, channelId, messageId);
        if (!message.authorId().equals(requesterId) || message.deleted()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "message author required");
        }
        requireRequest(request);
        return MessageResponse.from(messageService.edit(new EditMessageCommand(guildId, channelId, messageId, request.content())));
    }

    @DeleteMapping("/{messageId}")
    ResponseEntity<Void> delete(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildIdFor(channelId);
        Message message = messageService.message(guildId, channelId, messageId);
        boolean author = message.authorId().equals(requesterId)
            && !message.deleted()
            && guildService.canViewChannel(guildId, channelId, requesterId);
        if (!author && !guildService.canManageMessages(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage messages permission required");
        }
        messageService.delete(guildId, channelId, messageId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{messageId}/pin")
    MessageResponse pin(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireManageMessages(channelId, requesterId);
        return MessageResponse.from(messageService.pin(guildId, channelId, messageId));
    }

    @DeleteMapping("/{messageId}/pin")
    MessageResponse unpin(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireManageMessages(channelId, requesterId);
        return MessageResponse.from(messageService.unpin(guildId, channelId, messageId));
    }

    @GetMapping("/search")
    List<MessageResponse> search(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam String q,
        @RequestParam(defaultValue = "50") int limit
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireView(channelId, requesterId);
        return messageService.search(guildId, channelId, q, limit).stream()
            .map(MessageResponse::from)
            .toList();
    }

    private UUID requireView(UUID channelId, UUID requesterId) {
        UUID guildId = guildIdFor(channelId);
        if (!guildService.canViewChannel(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        return guildId;
    }

    private UUID requireManageMessages(UUID channelId, UUID requesterId) {
        UUID guildId = guildIdFor(channelId);
        if (!guildService.canManageMessages(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage messages permission required");
        }
        return guildId;
    }

    private UUID guildIdFor(UUID channelId) {
        return guildService.guildIdForChannel(channelId);
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record MessageContentRequest(String content) {
    }

    record MessagePageResponse(List<MessageResponse> messages, String nextCursor) {
        static MessagePageResponse from(MessagePage page) {
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
                message.content(),
                message.mentions(),
                message.pinned(),
                message.deleted(),
                message.edited(),
                message.editHistory().stream().map(MessageEditResponse::from).toList(),
                message.createdAt(),
                message.updatedAt()
            );
        }
    }

    record MessageEditResponse(String content, Instant editedAt) {
        static MessageEditResponse from(MessageEdit edit) {
            return new MessageEditResponse(edit.content(), edit.editedAt());
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
}
