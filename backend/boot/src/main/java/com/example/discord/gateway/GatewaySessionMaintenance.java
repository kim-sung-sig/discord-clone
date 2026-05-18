package com.example.discord.gateway;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class GatewaySessionMaintenance {
    private final InMemoryGatewayService gatewayService;
    private final GatewayWebSocketHandler webSocketHandler;

    GatewaySessionMaintenance(InMemoryGatewayService gatewayService, GatewayWebSocketHandler webSocketHandler) {
        this.gatewayService = gatewayService;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedDelayString = "${discord.gateway.session-maintenance-delay-ms:30000}")
    void closeTimedOutSessions() {
        gatewayService.closeTimedOutSessions();
        webSocketHandler.closeClosedGatewaySessions();
    }
}
