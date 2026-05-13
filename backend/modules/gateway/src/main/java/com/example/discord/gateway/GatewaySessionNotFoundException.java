package com.example.discord.gateway;

public final class GatewaySessionNotFoundException extends RuntimeException {
    public GatewaySessionNotFoundException(String message) {
        super(message);
    }
}
