package com.example.discord.identity;

import java.security.PublicKey;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;

public final class BearerTokenVerifier {
    private final AccessTokenService tokens;

    public BearerTokenVerifier(Map<String, PublicKey> publicKeys, String issuer, String audience, Clock clock) {
        this.tokens = new AccessTokenService(publicKeys, issuer, audience, clock);
    }

    public UUID requireUserId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() == "Bearer ".length()) {
            throw new IllegalArgumentException("access token invalid");
        }
        try {
            return tokens.verify(authorization.substring("Bearer ".length())).userId();
        } catch (TokenVerificationException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("access token invalid");
        }
    }
}
