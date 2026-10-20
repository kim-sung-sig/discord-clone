package com.example.discord.event;

import com.example.discord.guild.GuildMember;
import com.example.discord.guild.InMemoryGuildService;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
class ServerEventRecipientResolver implements Function<ServerEvent, Set<UUID>> {
    private final InMemoryGuildService guilds;

    ServerEventRecipientResolver(InMemoryGuildService guilds) {
        this.guilds = guilds;
    }

    @Override
    public Set<UUID> apply(ServerEvent event) {
        return guilds.guild(event.guildId()).members().stream()
            .map(GuildMember::userId)
            .filter(id -> guilds.canViewChannel(event.guildId(), event.channelId(), id))
            .collect(Collectors.toSet());
    }
}
