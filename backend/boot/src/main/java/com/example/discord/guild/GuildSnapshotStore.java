package com.example.discord.guild;

import java.util.List;

interface GuildSnapshotStore {
    List<Guild> loadAll();

    void save(Guild guild);
}
