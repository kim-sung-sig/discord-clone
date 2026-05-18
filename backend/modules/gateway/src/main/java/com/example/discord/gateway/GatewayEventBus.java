package com.example.discord.gateway;

import java.util.UUID;
import java.util.function.Consumer;

public interface GatewayEventBus {
    GatewayBusEvent publish(GatewayBusPublishCommand command);

    void addEventListener(Consumer<GatewayBusEvent> listener);

    default void subscribeGuild(UUID guildId) {
    }

    default void subscribeChannel(UUID channelId) {
    }
}
