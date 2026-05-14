package com.example.discord.experience;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import java.util.Set;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
class StageController {
    private final InMemoryExperienceService experienceService;
    private final InMemoryGuildService guildService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    StageController(
        InMemoryExperienceService experienceService,
        InMemoryGuildService guildService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.experienceService = experienceService;
        this.guildService = guildService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/api/stage/channels/{channelId}/sessions")
    ResponseEntity<StageSessionResponse> startSession(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody StartStageSessionRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildService.guildIdForChannel(channelId);
        requireStageModerator(guildId, requesterId);
        requireRequest(request);
        StageSession session = experienceService.startStageSession(guildId, channelId, request.topic(), requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(StageSessionResponse.from(session));
    }

    @PostMapping("/api/stage/sessions/{sessionId}/request-to-speak")
    StageSessionResponse requestToSpeak(
        @PathVariable UUID sessionId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        StageSession session = experienceService.stageSession(sessionId);
        requireViewChannel(session.guildId(), session.channelId(), requesterId);
        return StageSessionResponse.from(experienceService.requestToSpeak(sessionId, requesterId));
    }

    @PutMapping("/api/stage/sessions/{sessionId}/speakers/{userId}")
    StageSessionResponse approveSpeaker(
        @PathVariable UUID sessionId,
        @PathVariable UUID userId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        StageSession session = experienceService.stageSession(sessionId);
        requireStageModerator(session.guildId(), requesterId);
        return StageSessionResponse.from(experienceService.approveSpeaker(sessionId, userId));
    }

    @PutMapping("/api/stage/sessions/{sessionId}/audience/{userId}")
    StageSessionResponse moveToAudience(
        @PathVariable UUID sessionId,
        @PathVariable UUID userId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        StageSession session = experienceService.stageSession(sessionId);
        requireStageModerator(session.guildId(), requesterId);
        return StageSessionResponse.from(experienceService.moveToAudience(sessionId, userId));
    }

    @GetMapping("/api/stage/channels/{channelId}/sessions/active")
    StageSessionResponse activeSession(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildService.guildIdForChannel(channelId);
        requireViewChannel(guildId, channelId, requesterId);
        return StageSessionResponse.from(experienceService.activeStageSession(channelId));
    }

    private void requireStageModerator(UUID guildId, UUID requesterId) {
        if (!guildService.canManageChannels(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "stage moderator permission required");
        }
    }

    private void requireViewChannel(UUID guildId, UUID channelId, UUID requesterId) {
        if (!guildService.canViewChannel(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record StartStageSessionRequest(String topic) {
    }

    record StageSessionResponse(
        UUID id,
        UUID guildId,
        UUID channelId,
        String topic,
        Set<UUID> moderatorIds,
        Set<UUID> speakerIds,
        Set<UUID> audienceIds,
        Set<UUID> pendingSpeakerIds
    ) {
        static StageSessionResponse from(StageSession session) {
            return new StageSessionResponse(
                session.id(),
                session.guildId(),
                session.channelId(),
                session.topic(),
                session.moderatorIds(),
                session.speakerIds(),
                session.audienceIds(),
                session.pendingSpeakerIds()
            );
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = StageController.class)
class StageControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<StageController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new StageController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<StageController.ErrorResponse> invalidState(IllegalStateException exception) {
        return ResponseEntity.badRequest().body(new StageController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<StageController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new StageController.ErrorResponse("invalid request"));
    }
}
