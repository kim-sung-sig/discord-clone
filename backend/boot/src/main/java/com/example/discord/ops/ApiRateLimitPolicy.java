package com.example.discord.ops;

import java.time.Duration;
import java.util.Optional;

final class ApiRateLimitPolicy {
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final RateLimitPolicy AUTH_LOGIN = new RateLimitPolicy("auth-login", 2, ONE_MINUTE);
    private static final RateLimitPolicy INVITE_ACCEPT = new RateLimitPolicy("invite-accept", 10, ONE_MINUTE);
    private static final RateLimitPolicy MESSAGE_CREATE = new RateLimitPolicy("message-create", 30, ONE_MINUTE);
    private static final RateLimitPolicy GATEWAY_IDENTIFY = new RateLimitPolicy("gateway-identify", 10, ONE_MINUTE);

    private ApiRateLimitPolicy() {
    }

    static Optional<RateLimitPolicy> forRequest(String method, String normalizedPath) {
        if (!"POST".equals(method)) {
            return Optional.empty();
        }
        return switch (normalizedPath) {
            case "/api/auth/login" -> Optional.of(AUTH_LOGIN);
            case "/api/invites/{token}/accept" -> Optional.of(INVITE_ACCEPT);
            case "/api/channels/{uuid}/messages" -> Optional.of(MESSAGE_CREATE);
            case "/api/gateway/identify" -> Optional.of(GATEWAY_IDENTIFY);
            default -> Optional.empty();
        };
    }
}
