package com.example.discord.gateway;

public final class GatewayAckOutOfRangeException extends IllegalArgumentException {
    public GatewayAckOutOfRangeException(String message) {
        super(message);
    }
}
