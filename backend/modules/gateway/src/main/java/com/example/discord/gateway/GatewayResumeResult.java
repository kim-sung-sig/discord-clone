package com.example.discord.gateway;

import java.util.List;
import java.util.Objects;

public record GatewayResumeResult(GatewaySession session, GatewayEvent resumed, List<GatewayEvent> events) {
    public GatewayResumeResult {
        Objects.requireNonNull(session, "session must not be null");
        Objects.requireNonNull(resumed, "resumed must not be null");
        events = List.copyOf(Objects.requireNonNull(events, "events must not be null"));
    }
}
