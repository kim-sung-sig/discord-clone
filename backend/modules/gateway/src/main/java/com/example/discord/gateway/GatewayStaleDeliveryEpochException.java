package com.example.discord.gateway;

public final class GatewayStaleDeliveryEpochException extends IllegalStateException {
    public GatewayStaleDeliveryEpochException(String message) {
        super(message);
    }
}
