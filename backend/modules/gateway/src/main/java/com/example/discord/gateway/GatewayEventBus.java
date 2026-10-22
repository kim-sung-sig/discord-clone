package com.example.discord.gateway;

import java.util.UUID;
import java.util.function.Consumer;

public interface GatewayEventBus {
    GatewayBusEvent publish(GatewayBusPublishCommand command);

    void addEventListener(Consumer<GatewayBusEvent> listener);

    /** Buses that route by guild/channel subscription (Redis) need sessions to register their visible channels. */
    default boolean tracksSubscriptions() {
        return false;
    }

    default void subscribeGuild(UUID guildId) {
    }

    default void subscribeChannel(UUID channelId) {
    }
}
