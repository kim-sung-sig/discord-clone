package com.example.discord.invite;

import java.util.List;

interface InviteSnapshotStore {
    List<Invite> loadAll();

    void save(Invite invite);
}
