package com.example.discord.gateway;

import java.util.Objects;

public record GatewayHeartbeatResult(GatewaySession session, GatewayEvent ack) {
    public GatewayHeartbeatResult {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(ack, "ack must not be null");
    }
}
