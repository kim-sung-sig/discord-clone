package com.example.discord.gateway;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class GatewaySessionMaintenance {
    private final InMemoryGatewayService gatewayService;

    GatewaySessionMaintenance(InMemoryGatewayService gatewayService) {
        this.gatewayService = gatewayService;
    }

    @Scheduled(fixedDelayString = "${discord.gateway.session-maintenance-delay-ms:30000}")
    void closeTimedOutSessions() {
        gatewayService.closeTimedOutSessions();
    }
}
