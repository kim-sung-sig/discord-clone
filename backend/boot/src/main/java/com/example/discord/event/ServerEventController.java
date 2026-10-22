package com.example.discord.event;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.Channel;
import com.example.discord.guild.GuildService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("postgres")
@RequestMapping("/api/guilds/{guildId}/events")
class ServerEventController {
    private final AuthenticatedUserResolver users;
    private final GuildService guilds;
    private final JdbcServerEventService events;

    ServerEventController(AuthenticatedUserResolver users, GuildService guilds, JdbcServerEventService events) {
        this.users = users;
        this.guilds = guilds;
        this.events = events;
    }

    @GetMapping
    List<ServerEvent> list(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID userId = users.requireUserId(authorization);
        if (!guilds.guildIdsForMember(userId).contains(guildId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "guild membership required");
        }
        return events.visibleEvents(guildId, visibleChannels(guildId, userId));
    }

    @GetMapping("/{eventId}")
    ServerEvent get(
        @PathVariable UUID guildId,
        @PathVariable UUID eventId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID userId = users.requireUserId(authorization);
        if (!guilds.guildIdsForMember(userId).contains(guildId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "guild membership required");
        }
        ServerEvent event = events.event(guildId, eventId);
        if (!guilds.canViewChannel(guildId, event.channelId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        return event;
    }

    @PostMapping
    ResponseEntity<ServerEvent> create(
        @PathVariable UUID guildId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody CreateRequest request
    ) {
        UUID userId = users.requireUserId(authorization);
        if (!guilds.canManageChannels(guildId, userId)
            || !guilds.canViewChannel(guildId, request.channelId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "event management permission required");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(events.createEvent(
            new CreateServerEventCommand(guildId, request.channelId(), userId, request.title(), request.startsAt(), request.endsAt()),
            true
        ));
    }

    @PutMapping("/{eventId}/cancel")
    ServerEvent cancel(
        @PathVariable UUID guildId,
        @PathVariable UUID eventId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestBody CancelRequest request
    ) {
        UUID userId = users.requireUserId(authorization);
        if (!guilds.canManageChannels(guildId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "event management permission required");
        }
        ServerEvent event = events.event(guildId, eventId);
        if (!guilds.canViewChannel(guildId, event.channelId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "view channel permission required");
        }
        return events.cancelEvent(guildId, eventId, userId, request == null ? "" : request.reason());
    }

    private Set<UUID> visibleChannels(UUID guildId, UUID userId) {
        return guilds.visibleChannels(guildId, userId).stream().map(Channel::id).collect(Collectors.toSet());
    }

    record CreateRequest(@NotNull UUID channelId, @NotBlank @Size(max = 160) String title,
                         @NotNull Instant startsAt, @NotNull Instant endsAt) {
    }

    record CancelRequest(String reason) {
    }
}

@RestControllerAdvice(assignableTypes = ServerEventController.class)
class ServerEventControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Void> unreadableRequest() {
        return ResponseEntity.badRequest().build();
    }
}
