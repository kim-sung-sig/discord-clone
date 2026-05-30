package com.example.discord.presence;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
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
@RequestMapping("/api/presence")
class PresenceController {
    private final InMemoryPresenceService presenceService;
    private final InMemoryGuildService guildService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    PresenceController(
        InMemoryPresenceService presenceService,
        InMemoryGuildService guildService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.presenceService = presenceService;
        this.guildService = guildService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PutMapping("/me")
    PresenceResponse updateMe(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody PresenceUpdateRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        presenceService.updatePresence(requesterId, request.status(), ttl(request.ttlSeconds()));
        return PresenceResponse.from(presenceService.presence(requesterId));
    }

    @GetMapping("/users/{userId}")
    PresenceResponse userPresence(
        @PathVariable UUID userId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        authenticatedUserResolver.requireUserId(authorization);
        return PresenceResponse.from(presenceService.presence(userId));
    }

    @PutMapping("/channels/{channelId}/typing")
    ResponseEntity<Void> startTyping(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody TypingRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireCanViewChannel(channelId, requesterId);
        requireRequest(request);
        presenceService.startTyping(channelId, requesterId, ttl(request.ttlSeconds()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/channels/{channelId}/typing")
    TypingUsersResponse typingUsers(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireCanViewChannel(channelId, requesterId);
        return new TypingUsersResponse(presenceService.typingUsers(channelId));
    }

    @PutMapping("/channels/{channelId}/read")
    ReadMarkerResponse markRead(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody ReadMarkerRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireCanViewChannel(channelId, requesterId);
        requireRequest(request);
        return ReadMarkerResponse.from(presenceService.markRead(channelId, requesterId, request.lastReadSequence()));
    }

    @PostMapping("/channels/{channelId}/unread-count")
    UnreadCountResponse unreadCount(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody UnreadCountRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        requireCanViewChannel(channelId, requesterId);
        requireRequest(request);
        return new UnreadCountResponse(presenceService.unreadCount(
            channelId,
            requesterId,
            request.lastMessageSequence(),
            request.authoredSequences()
        ));
    }

    private void requireCanViewChannel(UUID channelId, UUID requesterId) {
        UUID guildId = guildService.guildIdForChannel(channelId);
        if (!guildService.canViewChannel(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    private static Duration ttl(long ttlSeconds) {
        if (ttlSeconds < 1) {
            throw new IllegalArgumentException("ttlSeconds must be positive");
        }
        return Duration.ofSeconds(ttlSeconds);
    }

    record PresenceUpdateRequest(PresenceStatus status, long ttlSeconds) {
    }

    record TypingRequest(long ttlSeconds) {
    }

    record ReadMarkerRequest(long lastReadSequence) {
    }

    record UnreadCountRequest(long lastMessageSequence, List<Long> authoredSequences) {
        UnreadCountRequest {
            authoredSequences = authoredSequences == null ? List.of() : List.copyOf(authoredSequences);
        }
    }

    record PresenceResponse(UUID userId, PresenceStatus status, Instant updatedAt) {
        static PresenceResponse from(UserPresence presence) {
            return new PresenceResponse(presence.userId(), presence.status(), presence.updatedAt());
        }
    }

    record TypingUsersResponse(List<UUID> typingUserIds) {
    }

    record ReadMarkerResponse(UUID channelId, UUID userId, long lastReadSequence, Instant updatedAt) {
        static ReadMarkerResponse from(ReadMarker marker) {
            return new ReadMarkerResponse(
                marker.channelId(),
                marker.userId(),
                marker.lastReadSequence(),
                marker.updatedAt()
            );
        }
    }

    record UnreadCountResponse(long unreadCount) {
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = PresenceController.class)
class PresenceControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<PresenceController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new PresenceController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<PresenceController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new PresenceController.ErrorResponse("invalid request"));
    }
}
