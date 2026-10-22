package com.example.discord.guild;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

public interface GuildStore {
    Guild get(UUID guildId);

    void create(Guild guild);

    <T> T update(UUID guildId, Function<Guild, T> mutation);

    List<UUID> guildIdsForMember(UUID memberId);

    UUID guildIdForChannel(UUID channelId);
}
