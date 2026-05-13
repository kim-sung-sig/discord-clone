package com.example.discord.gateway;

import java.util.Objects;

public record GatewayIdentifyResult(GatewaySession session, GatewayEvent ready) {
    public GatewayIdentifyResult {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(ready, "ready must not be null");
    }
}
