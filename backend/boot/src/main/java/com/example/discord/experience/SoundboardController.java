package com.example.discord.experience;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
class SoundboardController {
    private final InMemoryExperienceService experienceService;
    private final InMemoryGuildService guildService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    SoundboardController(
        InMemoryExperienceService experienceService,
        InMemoryGuildService guildService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.experienceService = experienceService;
        this.guildService = guildService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/api/soundboard/guilds/{guildId}/sounds")
    ResponseEntity<SoundboardSoundResponse> createSound(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CreateSoundRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.canManageExpressions(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "manage expressions permission required");
        }
        requireRequest(request);
        SoundboardSound sound = experienceService.registerSound(guildId, request.name(), request.objectKey(), requesterId);
        return ResponseEntity.status(HttpStatus.CREATED).body(SoundboardSoundResponse.from(sound));
    }

    @GetMapping("/api/soundboard/guilds/{guildId}/sounds")
    List<SoundboardSoundResponse> sounds(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        if (!guildService.isGuildMemberOrOwner(guildId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "guild membership required");
        }
        return experienceService.sounds(guildId).stream()
            .map(SoundboardSoundResponse::from)
            .toList();
    }

    @PostMapping("/api/soundboard/channels/{channelId}/sounds/{soundId}/play")
    SoundboardPlayEventResponse playSound(
        @PathVariable UUID channelId,
        @PathVariable UUID soundId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = guildService.guildIdForChannel(channelId);
        if (!guildService.canViewChannel(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        SoundboardSound sound = experienceService.sound(soundId);
        if (!sound.guildId().equals(guildId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "sound not found");
        }
        return SoundboardPlayEventResponse.from(experienceService.playSound(channelId, soundId, requesterId));
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record CreateSoundRequest(String name, String objectKey) {
    }

    record SoundboardSoundResponse(UUID id, UUID guildId, String name, String objectKey, UUID creatorId) {
        static SoundboardSoundResponse from(SoundboardSound sound) {
            return new SoundboardSoundResponse(sound.id(), sound.guildId(), sound.name(), sound.objectKey(), sound.creatorId());
        }
    }

    record SoundboardPlayEventResponse(UUID id, UUID channelId, UUID soundId, UUID userId) {
        static SoundboardPlayEventResponse from(SoundboardPlayEvent event) {
            return new SoundboardPlayEventResponse(event.id(), event.channelId(), event.soundId(), event.userId());
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = SoundboardController.class)
class SoundboardControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<SoundboardController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new SoundboardController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<SoundboardController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new SoundboardController.ErrorResponse("invalid request"));
    }
}
