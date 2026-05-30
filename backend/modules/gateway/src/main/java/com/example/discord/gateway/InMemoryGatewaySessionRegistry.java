package com.example.discord.gateway;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryGatewaySessionRegistry implements GatewaySessionRegistry {
    private final Map<UUID, GatewaySession> sessions = new LinkedHashMap<>();

    @Override
    public synchronized void save(GatewaySession session) {
        sessions.put(session.id(), session);
    }

    @Override
    public synchronized Optional<GatewaySession> find(UUID sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public synchronized Collection<GatewaySession> sessions() {
        return List.copyOf(sessions.values());
    }
}
