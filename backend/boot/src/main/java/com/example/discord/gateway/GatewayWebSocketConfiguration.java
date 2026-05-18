package com.example.discord.gateway;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
class GatewayWebSocketConfiguration implements WebSocketConfigurer {
    private final GatewayWebSocketHandler handler;

    GatewayWebSocketConfiguration(GatewayWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/gateway")
            .setAllowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*");
    }
}
