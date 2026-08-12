package com.example.discord.gateway;

public final class GatewayEventConflictException extends IllegalArgumentException {
    public GatewayEventConflictException(String message) {
        super(message);
    }
}
