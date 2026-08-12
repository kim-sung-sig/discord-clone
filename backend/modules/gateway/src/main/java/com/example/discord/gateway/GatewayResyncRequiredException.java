package com.example.discord.gateway;

public final class GatewayResyncRequiredException extends IllegalStateException {
    public GatewayResyncRequiredException(String message) {
        super(message);
    }
}
