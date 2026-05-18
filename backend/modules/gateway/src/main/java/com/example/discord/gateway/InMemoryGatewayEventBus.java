package com.example.discord.gateway;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

public final class InMemoryGatewayEventBus implements GatewayEventBus {
    private final Clock clock;
    private final Map<String, GatewayBusEvent> eventsById = new LinkedHashMap<>();
    private final List<Consumer<GatewayBusEvent>> listeners = new java.util.ArrayList<>();

    public InMemoryGatewayEventBus(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public synchronized GatewayBusEvent publish(GatewayBusPublishCommand command) {
        GatewayBusEvent event = new GatewayBusEvent(
            UUID.randomUUID().toString(),
            command.type(),
            command.guildId(),
            command.channelId(),
            command.payload(),
            clock.instant()
        );
        eventsById.put(event.eventId(), event);
        notifyListeners(event);
        return event;
    }

    @Override
    public synchronized void addEventListener(Consumer<GatewayBusEvent> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener must not be null"));
    }

    public synchronized void redeliver(String eventId) {
        GatewayBusEvent event = eventsById.get(eventId);
        if (event == null) {
            throw new IllegalArgumentException("gateway bus event not found");
        }
        notifyListeners(event);
    }

    private void notifyListeners(GatewayBusEvent event) {
        for (Consumer<GatewayBusEvent> listener : List.copyOf(listeners)) {
            listener.accept(event);
        }
    }
}
