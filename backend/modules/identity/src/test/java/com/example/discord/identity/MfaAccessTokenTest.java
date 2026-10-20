package com.example.discord.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jsonwebtoken.Jwts;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MfaAccessTokenTest {
    @Test
    void temporaryCapabilityCannotBeAcceptedByExistingConsumers() throws Exception {
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        var verifier = new AccessTokenService(Map.of("test", key.getPublic()), "identity", "api", Clock.fixed(now, ZoneOffset.UTC));
        String token = Jwts.builder().header().keyId("test").and().issuer("identity").audience().add("api").and()
            .subject(UUID.randomUUID().toString()).issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(60)))
            .claim("typ", "auth-challenge").signWith(key.getPrivate(), Jwts.SIG.EdDSA).compact();
        assertThrows(TokenVerificationException.class, () -> verifier.verify(token));
    }

    @Test
    void elevatedEvidenceHasBoundedLifetime() throws Exception {
        var key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant now = Instant.parse("2026-09-14T00:00:00Z");
        var service = new AccessTokenService(key.getPrivate(), Map.of("test", key.getPublic()), "test", "identity", "api",
            Duration.ofHours(1), Clock.fixed(now, ZoneOffset.UTC));
        var claims = service.verify(service.issueMfa(UUID.randomUUID(), "totp"));
        assertEquals(now.plusSeconds(300), claims.expiresAt());
        assertEquals(java.util.List.of("totp"), claims.amr());
        assertEquals(now, claims.authTime());
    }
}
