package com.example.discord.gateway;

import java.util.List;

/** Durable boundary for Gateway event sequence and idempotent append. */
public interface GatewayEventLog {
    long allocateSequence();

    GatewayEvent append(GatewayBusEvent event);

    List<GatewayEvent> after(long sequence, int limit);

    long oldestSequence();

    long latestSequence();
}
