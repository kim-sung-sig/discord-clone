package com.example.discord.event;

import com.example.discord.guild.Guild;
import com.example.discord.guild.GuildMember;
import com.example.discord.guild.GuildService;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
class ServerEventRecipientResolver implements Function<ServerEvent, Set<UUID>> {
    private final GuildService guilds;

    ServerEventRecipientResolver(GuildService guilds) {
        this.guilds = guilds;
    }

    @Override
    public Set<UUID> apply(ServerEvent event) {
        Guild guild = guilds.guild(event.guildId());
        return Set.copyOf(guilds.channelViewers(
            guild,
            event.channelId(),
            guild.members().stream().map(GuildMember::userId).toList()
        ));
    }
}
