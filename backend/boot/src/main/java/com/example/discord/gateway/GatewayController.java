package com.example.discord.gateway;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/gateway")
class GatewayController {
    private static final String INTERNAL_PUBLISHER_HEADER = "X-Internal-Gateway-Publisher";

    private final InMemoryGatewayService gatewayService;
    private final InMemoryGuildService guildService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final String internalPublisherToken;

    GatewayController(
        InMemoryGatewayService gatewayService,
        InMemoryGuildService guildService,
        AuthenticatedUserResolver authenticatedUserResolver,
        @Value("${discord.gateway.internal-publisher-token:}") String internalPublisherToken
    ) {
        this.gatewayService = gatewayService;
        this.guildService = guildService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.internalPublisherToken = internalPublisherToken;
    }

    @PostMapping("/identify")
    IdentifyResponse identify(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID userId = authenticatedUserResolver.requireUserId(authorization);
        GatewayIdentifyResult result = gatewayService.identify(userId);
        return new IdentifyResponse(SessionResponse.from(result.session()), EventResponse.from(result.ready()));
    }

    @PostMapping("/sessions/{sessionId}/heartbeat")
    HeartbeatResponse heartbeat(
        @PathVariable UUID sessionId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(authorization);
        GatewayHeartbeatResult result = gatewayService.heartbeat(sessionId, userId);
        return new HeartbeatResponse(SessionResponse.from(result.session()), EventResponse.from(result.ack()));
    }

    @PostMapping("/sessions/{sessionId}/resume")
    ResumeResponse resume(
        @PathVariable UUID sessionId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody ResumeRequest request
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        GatewayResumeResult result = gatewayService.resume(sessionId, userId, request.lastSeq());
        return new ResumeResponse(
            SessionResponse.from(result.session()),
            EventResponse.from(result.resumed()),
            result.events().stream().map(EventResponse::from).toList()
        );
    }

    @GetMapping("/sessions/{sessionId}/events")
    EventsResponse events(
        @PathVariable UUID sessionId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(defaultValue = "0") long afterSeq
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(authorization);
        return new EventsResponse(
            gatewayService.poll(sessionId, userId, afterSeq).stream()
                .map(EventResponse::from)
                .toList()
        );
    }

    @PostMapping("/events")
    ResponseEntity<EventResponse> publish(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestHeader(value = INTERNAL_PUBLISHER_HEADER, required = false) String internalPublisher,
        @Valid @RequestBody PublishEventRequest request
    ) {
        UUID userId = authenticatedUserResolver.requireUserId(authorization);
        requireRequest(request);
        requireInternalPublisher(internalPublisher);
        if (!guildService.isGuildMemberOrOwner(request.guildId(), userId)) {
            throw new GatewayForbiddenException("guild membership required");
        }
        if (request.channelId() != null) {
            if (!guildService.channelBelongsToGuild(request.guildId(), request.channelId())) {
                throw new IllegalArgumentException("channel does not belong to guild");
            }
            if (!guildService.canViewChannel(request.guildId(), request.channelId(), userId)) {
                throw new GatewayForbiddenException("channel visibility required");
            }
        }
        GatewayEvent event = gatewayService.publish(request.type(), request.guildId(), request.channelId(), request.payload());
        return ResponseEntity.status(HttpStatus.CREATED).body(EventResponse.from(event));
    }

    private void requireInternalPublisher(String internalPublisher) {
        if (internalPublisherToken == null || internalPublisherToken.isBlank()) {
            throw new GatewayForbiddenException("internal gateway publisher disabled");
        }
        if (!secureEquals(internalPublisherToken, internalPublisher)) {
            throw new GatewayForbiddenException("internal gateway publisher required");
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

    private static void requireRequest(Object request) {
        if (request == null) {
            throw new IllegalArgumentException("request body is required");
        }
    }

    record IdentifyResponse(SessionResponse session, EventResponse ready) {
    }

    record HeartbeatResponse(SessionResponse session, EventResponse ack) {
    }

    record ResumeRequest(@PositiveOrZero long lastSeq) {
    }

    record ResumeResponse(SessionResponse session, EventResponse resumed, List<EventResponse> events) {
    }

    record EventsResponse(List<EventResponse> events) {
    }

    record PublishEventRequest(
        @NotBlank String type,
        @NotNull UUID guildId,
        UUID channelId,
        @NotNull Map<String, Object> payload
    ) {
    }

    record SessionResponse(
        UUID id,
        UUID userId,
        List<UUID> guildIds,
        Instant lastAcknowledgedAt,
        boolean closed,
        long lastDeliveredSequence
    ) {
        static SessionResponse from(GatewaySession session) {
            return new SessionResponse(
                session.id(),
                session.userId(),
                session.guildIds().stream().sorted().toList(),
                session.lastAcknowledgedAt(),
                session.closed(),
                session.lastDeliveredSequence()
            );
        }
    }

    record EventResponse(
        long sequence,
        String type,
        UUID guildId,
        UUID channelId,
        Map<String, Object> payload,
        Instant createdAt
    ) {
        static EventResponse from(GatewayEvent event) {
            return new EventResponse(
                event.sequence(),
                event.type(),
                event.guildId(),
                event.channelId(),
                event.payload(),
                event.createdAt()
            );
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = GatewayController.class)
class GatewayControllerAdvice {
    @ExceptionHandler(GatewayForbiddenException.class)
    ResponseEntity<GatewayController.ErrorResponse> forbidden(GatewayForbiddenException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(new GatewayController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(GatewaySessionNotFoundException.class)
    ResponseEntity<GatewayController.ErrorResponse> notFound(GatewaySessionNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new GatewayController.ErrorResponse(exception.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<GatewayController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
            .body(new GatewayController.ErrorResponse("invalid request"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<GatewayController.ErrorResponse> invalidBody(MethodArgumentNotValidException exception) {
        return ResponseEntity.badRequest()
            .body(new GatewayController.ErrorResponse("invalid request"));
    }
}
