package com.example.discord.identity;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenClaims(UUID userId, Instant issuedAt, Instant expiresAt, java.util.List<String> amr, Instant authTime) {
    public AccessTokenClaims {
        amr = java.util.List.copyOf(amr);
    }

    public AccessTokenClaims(UUID userId, Instant issuedAt, Instant expiresAt) {
        this(userId, issuedAt, expiresAt, java.util.List.of(), null);
    }
}
