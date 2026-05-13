package com.example.discord.gateway;

public final class GatewayForbiddenException extends RuntimeException {
    public GatewayForbiddenException(String message) {
        super(message);
    }
}
