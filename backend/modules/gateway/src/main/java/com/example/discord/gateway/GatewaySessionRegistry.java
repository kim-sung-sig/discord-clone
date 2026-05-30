package com.example.discord.gateway;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface GatewaySessionRegistry {
    void save(GatewaySession session);

    Optional<GatewaySession> find(UUID sessionId);

    Collection<GatewaySession> sessions();
}
