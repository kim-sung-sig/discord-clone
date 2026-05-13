package com.example.discord.identity;

import java.time.Instant;
import java.util.UUID;

public record AccessTokenClaims(UUID userId, Instant issuedAt, Instant expiresAt) {
}
