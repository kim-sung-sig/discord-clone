package com.example.discord.message;

import java.util.UUID;

class PersistentMessageService extends InMemoryMessageService {
    private final MessageSnapshotStore snapshots;

    PersistentMessageService(MessageSnapshotStore snapshots) {
        this.snapshots = snapshots;
        snapshots.loadAll().forEach(this::putMessage);
    }

    @Override
    public synchronized Message create(CreateMessageCommand command) {
        Message message = super.create(command);
        snapshots.save(message);
        return message;
    }

    @Override
    public synchronized Message edit(EditMessageCommand command) {
        Message message = super.edit(command);
        snapshots.save(message);
        return message;
    }

    @Override
    public synchronized Message delete(UUID guildId, UUID channelId, UUID messageId) {
        Message message = super.delete(guildId, channelId, messageId);
        snapshots.save(message);
        return message;
    }

    @Override
    public synchronized Message pin(UUID guildId, UUID channelId, UUID messageId) {
        Message message = super.pin(guildId, channelId, messageId);
        snapshots.save(message);
        return message;
    }

    @Override
    public synchronized Message unpin(UUID guildId, UUID channelId, UUID messageId) {
        Message message = super.unpin(guildId, channelId, messageId);
        snapshots.save(message);
        return message;
    }
}
