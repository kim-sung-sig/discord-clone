package com.example.discord.invite;

import java.time.Clock;
import java.util.UUID;

class PersistentInviteService extends InMemoryInviteService {
    private final InviteSnapshotStore snapshots;

    PersistentInviteService(InviteSnapshotStore snapshots, Clock clock) {
        super(clock);
        this.snapshots = snapshots;
        snapshots.loadAll().forEach(this::putInvite);
    }

    @Override
    public synchronized Invite create(CreateInviteCommand command) {
        Invite invite = super.create(command);
        snapshots.save(invite);
        return invite;
    }

    @Override
    public synchronized InviteAcceptResult accept(String code, UUID memberId) {
        InviteAcceptResult result = super.accept(code, memberId);
        snapshots.save(result.invite());
        return result;
    }

    @Override
    public synchronized Invite delete(String code) {
        Invite invite = super.delete(code);
        snapshots.save(invite);
        return invite;
    }
}
