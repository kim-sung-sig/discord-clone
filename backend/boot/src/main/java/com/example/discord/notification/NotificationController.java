package com.example.discord.notification;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.guild.InMemoryGuildService;
import com.example.discord.guild.Channel;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("postgres")
@RequestMapping("/api/notifications")
class NotificationController {
    private final AuthenticatedUserResolver users;
    private final InMemoryGuildService guilds;
    private final JdbcNotificationInboxService inbox;

    NotificationController(AuthenticatedUserResolver users, InMemoryGuildService guilds, JdbcNotificationInboxService inbox) {
        this.users = users;
        this.guilds = guilds;
        this.inbox = inbox;
    }

    @GetMapping
    List<NotificationItem> page(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @RequestParam(required = false) UUID before,
        @RequestParam(defaultValue = "50") int limit
    ) {
        UUID userId = users.requireUserId(authorization);
        return inbox.page(userId, visibleChannels(userId), before, limit);
    }

    @GetMapping("/unread-count")
    UnreadCountResponse unreadCount(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID userId = users.requireUserId(authorization);
        return new UnreadCountResponse(inbox.unreadCount(userId, visibleChannels(userId)));
    }

    @PutMapping("/{itemId}/read")
    void markServerItemRead(
        @PathVariable UUID itemId,
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
    ) {
        UUID userId = users.requireUserId(authorization);
        if (!inbox.markServerItemRead(userId, itemId, visibleChannels(userId))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "server notification not found");
        }
    }

    private Set<UUID> visibleChannels(UUID userId) {
        return guilds.guildIdsForMember(userId).stream()
            .flatMap(guildId -> guilds.visibleChannels(guildId, userId).stream())
            .map(Channel::id)
            .collect(java.util.stream.Collectors.toSet());
    }

    record UnreadCountResponse(long unreadCount) {
    }
}

@RestControllerAdvice(assignableTypes = NotificationController.class)
class NotificationControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> invalidRequest() {
        return ResponseEntity.badRequest().build();
    }
}
