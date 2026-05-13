package com.example.discord.guild;

import com.example.discord.channel.ChannelType;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestController
@RequestMapping("/api/guilds")
class GuildController {
    private final InMemoryGuildService guildService;

    GuildController(InMemoryGuildService guildService) {
        this.guildService = guildService;
    }

    @PostMapping
    ResponseEntity<GuildResponse> createGuild(@RequestBody CreateGuildRequest request) {
        Guild guild = guildService.createGuild(request.name(), request.ownerId());
        return ResponseEntity.status(HttpStatus.CREATED).body(GuildResponse.from(guild));
    }

    @PostMapping("/{guildId}/channels")
    ResponseEntity<ChannelResponse> createChannel(
        @PathVariable UUID guildId,
        @RequestBody CreateChannelRequest request
    ) {
        if (request.type() == null) {
            throw new IllegalArgumentException("channel type is required");
        }
        Channel channel = guildService.createChannel(guildId, request.name(), request.type(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ChannelResponse.from(channel));
    }

    @GetMapping("/{guildId}/channels/visible")
    List<ChannelResponse> visibleChannels(@PathVariable UUID guildId, @RequestParam UUID memberId) {
        return guildService.visibleChannels(guildId, memberId).stream()
            .map(ChannelResponse::from)
            .toList();
    }

    record CreateGuildRequest(String name, UUID ownerId) {
    }

    record CreateChannelRequest(String name, ChannelType type, UUID parentId) {
    }

    record GuildResponse(UUID id, String name, UUID ownerId) {
        static GuildResponse from(Guild guild) {
            return new GuildResponse(guild.id(), guild.name(), guild.ownerId());
        }
    }

    record ChannelResponse(UUID id, String name, ChannelType type, UUID parentId) {
        static ChannelResponse from(Channel channel) {
            return new ChannelResponse(channel.id(), channel.name(), channel.type(), channel.parentId());
        }
    }

    record ErrorResponse(String message) {
    }
}

@RestControllerAdvice(assignableTypes = GuildController.class)
class GuildControllerAdvice {
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<GuildController.ErrorResponse> invalidRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(new GuildController.ErrorResponse("invalid request"));
    }
}
