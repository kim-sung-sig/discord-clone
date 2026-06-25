package com.example.discord.expression;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.message.ChannelMessageTarget;
import com.example.discord.message.MessageLookupPort;
import com.example.discord.message.MessageNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
class ExpressionController {
    private final InMemoryExpressionService expressionService;
    private final InMemoryGuildService guildService;
    private final MessageLookupPort messageLookup;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    ExpressionController(
        InMemoryExpressionService expressionService,
        InMemoryGuildService guildService,
        MessageLookupPort messageLookup,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.expressionService = expressionService;
        this.guildService = guildService;
        this.messageLookup = messageLookup;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/api/guilds/{guildId}/emojis")
    ResponseEntity<CustomEmojiResponse> createCustomEmoji(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateCustomEmojiRequest request
    ) {
        UUID requesterId = requireManageExpressions(guildId, authorization);
        requireRequest(request);
        CustomEmoji emoji = expressionService.createCustomEmoji(
            guildId,
            request.name(),
            request.imageObjectKey(),
            requesterId
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(CustomEmojiResponse.from(emoji));
    }

    @GetMapping("/api/guilds/{guildId}/emojis")
    List<CustomEmojiResponse> customEmojis(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        requireGuildMember(guildId, authorization);
        return expressionService.customEmojis(guildId).stream()
            .map(CustomEmojiResponse::from)
            .toList();
    }

    @DeleteMapping("/api/guilds/{guildId}/emojis/{emojiId}")
    ResponseEntity<Void> deleteCustomEmoji(
        @PathVariable UUID guildId,
        @PathVariable UUID emojiId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        requireManageExpressions(guildId, authorization);
        expressionService.deleteCustomEmoji(guildId, emojiId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/guilds/{guildId}/stickers")
    ResponseEntity<StickerResponse> createSticker(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateStickerRequest request
    ) {
        UUID requesterId = requireManageExpressions(guildId, authorization);
        requireRequest(request);
        Sticker sticker = expressionService.createSticker(guildId, request.name(), request.description(), requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(StickerResponse.from(sticker));
    }

    @GetMapping("/api/guilds/{guildId}/stickers")
    List<StickerResponse> stickers(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        requireGuildMember(guildId, authorization);
        return expressionService.stickers(guildId).stream()
            .map(StickerResponse::from)
            .toList();
    }

    @PutMapping("/api/channels/{channelId}/messages/{messageId}/reactions/{emojiKey}")
    ReactionSummaryResponse addReaction(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @PathVariable String emojiKey,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireReactableMessage(channelId, messageId, requesterId);
        expressionService.addReaction(channelId, messageId, emojiKey, requesterId);
        return summaryFor(channelId, messageId, emojiKey, requesterId);
    }

    @DeleteMapping("/api/channels/{channelId}/messages/{messageId}/reactions/{emojiKey}")
    ResponseEntity<Void> removeReaction(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @PathVariable String emojiKey,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireReactableMessage(channelId, messageId, requesterId);
        expressionService.removeReaction(channelId, messageId, emojiKey, requesterId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/channels/{channelId}/messages/{messageId}/reactions")
    List<ReactionSummaryResponse> reactions(
        @PathVariable UUID channelId,
        @PathVariable UUID messageId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireReactableMessage(channelId, messageId, requesterId);
        return expressionService.reactionSummaries(channelId, messageId).stream()
            .map(summary -> ReactionSummaryResponse.from(summary, requesterId))
            .toList();
    }

    private ReactionSummaryResponse summaryFor(UUID channelId, UUID messageId, String emojiKey, UUID requesterId) {
        return expressionService.reactionSummaries(channelId, messageId).stream()
            .filter(summary -> summary.emojiKey().equals(emojiKey))
            .findFirst()
            .map(summary -> ReactionSummaryResponse.from(summary, requesterId))
            .orElseGet(() -> new ReactionSummaryResponse(emojiKey, 0, false));
    }

    private UUID requireManageExpressions(UUID guildId, String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.canManageExpressions(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage expressions permission required");
        }
        return requesterId;
    }

    private void requireGuildMember(UUID guildId, String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.isGuildMemberOrOwner(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "guild membership required");
        }
    }

    private UUID requireViewChannel(UUID channelId, UUID requesterId) {
        UUID guildId = guildService.guildIdForChannel(channelId);
        if (!guildService.canViewChannel(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        return guildId;
    }

    private void requireReactableMessage(UUID channelId, UUID messageId, UUID requesterId) {
        UUID guildId = requireViewChannel(channelId, requesterId);
        messageLookup.requireMessage(new ChannelMessageTarget(guildId, channelId), messageId);
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record CreateCustomEmojiRequest(String name, String imageObjectKey) {
    }

    record CreateStickerRequest(String name, String description) {
    }

    record CustomEmojiResponse(UUID id, UUID guildId, String name, String imageObjectKey, UUID creatorId) {
        static CustomEmojiResponse from(CustomEmoji emoji) {
            return new CustomEmojiResponse(
                emoji.id(),
                emoji.guildId(),
                emoji.name(),
                emoji.imageObjectKey(),
                emoji.creatorId()
            );
        }
    }

    record StickerResponse(UUID id, UUID guildId, String name, String description, UUID creatorId) {
        static StickerResponse from(Sticker sticker) {
            return new StickerResponse(
                sticker.id(),
                sticker.guildId(),
                sticker.name(),
                sticker.description(),
                sticker.creatorId()
            );
        }
    }

    record ReactionSummaryResponse(String emojiKey, int count, boolean currentUserReacted) {
        static ReactionSummaryResponse from(ReactionSummary summary, UUID requesterId) {
            return new ReactionSummaryResponse(summary.emojiKey(), summary.count(), summary.reactedBy(requesterId));
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = ExpressionController.class)
class ExpressionControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ExpressionController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new ExpressionController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ExpressionController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new ExpressionController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(ExpressionNotFoundException.class)
    ResponseEntity<ExpressionController.ErrorResponse> missingExpression(ExpressionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ExpressionController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(MessageNotFoundException.class)
    ResponseEntity<ExpressionController.ErrorResponse> missingMessage(MessageNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ExpressionController.ErrorResponse(exception.getMessage()));
    }
}
