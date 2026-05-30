package com.example.discord.voice;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.channel.ChannelType;
import com.example.discord.gateway.InMemoryGatewayService;
import com.example.discord.guild.Channel;
import com.example.discord.guild.InMemoryGuildService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/voice")
class VoiceController {
    private final InMemoryVoiceService voiceService;
    private final InMemoryGuildService guildService;
    private final InMemoryGatewayService gatewayService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    VoiceController(
        InMemoryVoiceService voiceService,
        InMemoryGuildService guildService,
        InMemoryGatewayService gatewayService,
        AuthenticatedUserResolver authenticatedUserResolver
    ) {
        this.voiceService = voiceService;
        this.guildService = guildService;
        this.gatewayService = gatewayService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @PostMapping("/channels/{channelId}/join")
    VoiceJoinResponse join(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireVisibleVoiceChannel(channelId, requesterId);
        VoiceJoinResult result = voiceService.join(guildId, channelId, requesterId);
        publishVoiceState(VoiceStateEvent.JOINED, result.participant());
        return VoiceJoinResponse.from(result);
    }

    @DeleteMapping("/channels/{channelId}/leave")
    ResponseEntity<Void> leave(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireVoiceChannel(channelId);
        voiceService.leave(guildId, channelId, requesterId)
            .ifPresent(participant -> publishVoiceState(VoiceStateEvent.LEFT, participant));
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/channels/{channelId}/state")
    VoiceParticipantResponse updateState(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody VoiceStateRequest request
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireVisibleVoiceChannel(channelId, requesterId);
        requireRequest(request);
        VoiceParticipantState participant = voiceService.update(new VoiceStateUpdateCommand(
            guildId,
            channelId,
            requesterId,
            request.muted(),
            request.deafened(),
            request.speaking(),
            request.screenSharing()
        ));
        publishVoiceState(VoiceStateEvent.UPDATED, participant);
        return VoiceParticipantResponse.from(participant);
    }

    @GetMapping("/channels/{channelId}/participants")
    List<VoiceParticipantResponse> participants(
        @PathVariable UUID channelId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        UUID guildId = requireVisibleVoiceChannel(channelId, requesterId);
        return voiceService.participants(guildId, channelId).stream()
            .map(VoiceParticipantResponse::from)
            .toList();
    }

    @GetMapping("/events")
    List<VoiceStateEventResponse> events(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        return voiceService.events().stream()
            .filter(event -> guildService.canViewChannel(event.guildId(), event.channelId(), requesterId))
            .map(VoiceStateEventResponse::from)
            .toList();
    }

    private UUID requireVisibleVoiceChannel(UUID channelId, UUID requesterId) {
        UUID guildId = requireVoiceChannel(channelId);
        if (!guildService.canViewChannel(guildId, channelId, requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        return guildId;
    }

    private UUID requireVoiceChannel(UUID channelId) {
        UUID guildId = guildService.guildIdForChannel(channelId);
        Channel channel = guildService.channel(guildId, channelId);
        if (channel.type() != ChannelType.GUILD_VOICE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "voice channel required");
        }
        return guildId;
    }

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    private void publishVoiceState(String voiceEventType, VoiceParticipantState participant) {
        gatewayService.publish(
            VoiceStateEvent.UPDATED,
            participant.guildId(),
            participant.channelId(),
            Map.of(
                "voiceEventType", voiceEventType,
                "guildId", participant.guildId().toString(),
                "channelId", participant.channelId().toString(),
                "userId", participant.userId().toString(),
                "muted", participant.muted(),
                "deafened", participant.deafened(),
                "speaking", participant.speaking(),
                "screenSharing", participant.screenSharing()
            )
        );
    }

    record VoiceStateRequest(boolean muted, boolean deafened, boolean speaking, boolean screenSharing) {
    }

    record VoiceJoinResponse(VoiceParticipantResponse participant, VoiceTokenResponse token) {
        static VoiceJoinResponse from(VoiceJoinResult result) {
            return new VoiceJoinResponse(
                VoiceParticipantResponse.from(result.participant()),
                VoiceTokenResponse.from(result.token())
            );
        }
    }

    record VoiceTokenResponse(String room, String participant, String token, String provider, Instant expiresAt) {
        static VoiceTokenResponse from(VoiceRoomToken token) {
            return new VoiceTokenResponse(
                token.room(),
                token.participant(),
                token.token(),
                token.provider(),
                token.expiresAt()
            );
        }
    }

    record VoiceParticipantResponse(
        UUID guildId,
        UUID channelId,
        UUID userId,
        boolean muted,
        boolean deafened,
        boolean speaking,
        boolean screenSharing,
        Instant updatedAt
    ) {
        static VoiceParticipantResponse from(VoiceParticipantState participant) {
            return new VoiceParticipantResponse(
                participant.guildId(),
                participant.channelId(),
                participant.userId(),
                participant.muted(),
                participant.deafened(),
                participant.speaking(),
                participant.screenSharing(),
                participant.updatedAt()
            );
        }
    }

    record VoiceStateEventResponse(
        String type,
        UUID guildId,
        UUID channelId,
        UUID userId,
        boolean muted,
        boolean deafened,
        boolean speaking,
        boolean screenSharing,
        Instant occurredAt
    ) {
        static VoiceStateEventResponse from(VoiceStateEvent event) {
            return new VoiceStateEventResponse(
                event.type(),
                event.guildId(),
                event.channelId(),
                event.userId(),
                event.muted(),
                event.deafened(),
                event.speaking(),
                event.screenSharing(),
                event.occurredAt()
            );
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = VoiceController.class)
class VoiceControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<VoiceController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new VoiceController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<VoiceController.ErrorResponse> unreadableRequest(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(new VoiceController.ErrorResponse("invalid request"));
    }
}
