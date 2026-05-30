package com.example.discord.guild;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.example.discord.channel.ChannelType;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/@me/guilds")
class UserGuildController {
    private final InMemoryGuildService guildService;
    private final AuthenticatedUserResolver authenticatedUserResolver;

    UserGuildController(InMemoryGuildService guildService, AuthenticatedUserResolver authenticatedUserResolver) {
        this.guildService = guildService;
        this.authenticatedUserResolver = authenticatedUserResolver;
    }

    @GetMapping
    UserGuildsResponse guilds(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        UUID requesterId = authenticatedUserResolver.requireUserId(authorization);
        List<UserGuildResponse> guilds = guildService.guildIdsForMember(requesterId).stream()
            .map(guildId -> UserGuildResponse.from(guildService.guild(guildId), guildService.visibleChannels(guildId, requesterId)))
            .toList();
        return new UserGuildsResponse(guilds);
    }

    record UserGuildsResponse(List<UserGuildResponse> guilds) {
    }

    record UserGuildResponse(UUID id, String name, UUID ownerId, List<UserGuildChannelResponse> channels) {
        static UserGuildResponse from(Guild guild, List<Channel> channels) {
            return new UserGuildResponse(
                guild.id(),
                guild.name(),
                guild.ownerId(),
                channels.stream().map(UserGuildChannelResponse::from).toList()
            );
        }
    }

    record UserGuildChannelResponse(UUID id, String name, ChannelType type, UUID parentId) {
        static UserGuildChannelResponse from(Channel channel) {
            return new UserGuildChannelResponse(channel.id(), channel.name(), channel.type(), channel.parentId());
        }
    }
}
