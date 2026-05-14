package com.example.discord.message;

import java.util.List;

interface MessageSnapshotStore {
    List<Message> loadAll();

    void save(Message message);
}
