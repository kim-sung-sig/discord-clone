package com.example.discord.guild;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/** Infrastructure-free test double. Runtime configuration must use a persistent GuildStore. */
public final class InMemoryGuildService extends GuildService {
    public InMemoryGuildService() {
        super(new Store());
    }

    private static final class Store implements GuildStore {
        private final Map<UUID, Guild> guilds = new LinkedHashMap<>();

        @Override
        public synchronized Guild get(UUID guildId) {
            Guild guild = guilds.get(guildId);
            if (guild == null) {
                throw new IllegalArgumentException("guild not found");
            }
            return guild;
        }

        @Override
        public synchronized void create(Guild guild) {
            guilds.put(guild.id(), guild);
        }

        @Override
        public synchronized <T> T update(UUID guildId, Function<Guild, T> mutation) {
            return mutation.apply(get(guildId));
        }

        @Override
        public synchronized List<UUID> guildIdsForMember(UUID memberId) {
            return guilds.values().stream()
                .filter(guild -> guild.ownerId().equals(memberId)
                    || guild.members().stream().anyMatch(member -> member.userId().equals(memberId)))
                .map(Guild::id).toList();
        }

        @Override
        public synchronized UUID guildIdForChannel(UUID channelId) {
            return guilds.values().stream()
                .filter(guild -> guild.channels().stream().anyMatch(channel -> channel.id().equals(channelId)))
                .map(Guild::id).findFirst().orElseThrow(() -> new IllegalArgumentException("channel not found"));
        }
    }
}
