package com.example.discord.identity;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Claims;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.EdECKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

public final class AccessTokenService {
    private final PrivateKey privateKey; private final Map<String, PublicKey> publicKeys; private final String kid; private final String issuer; private final String audience; private final Duration ttl; private final Clock clock;
    public AccessTokenService(PrivateKey privateKey, String kid, String issuer, String audience, Duration ttl, Clock clock) {
        this(privateKey, Map.of(), kid, issuer, audience, ttl, clock);
    }
    public AccessTokenService(PrivateKey privateKey, Map<String, PublicKey> publicKeys, String kid, String issuer, String audience, Duration ttl, Clock clock) {
        if (!ed25519(privateKey) || blank(kid) || blank(issuer) || blank(audience) || ttl == null || !ttl.isPositive() || clock == null) throw new IllegalArgumentException("access token configuration invalid");
        if (publicKeys == null || publicKeys.entrySet().stream().anyMatch(e -> blank(e.getKey()) || !ed25519(e.getValue()))) throw new IllegalArgumentException("access token configuration invalid");
        this.privateKey=privateKey; this.publicKeys=Map.copyOf(publicKeys); this.kid=kid; this.issuer=issuer; this.audience=audience; this.ttl=ttl; this.clock=clock;
    }
    public AccessTokenService(Map<String, PublicKey> publicKeys, String issuer, String audience, Clock clock) {
        if (publicKeys == null || publicKeys.isEmpty() || publicKeys.entrySet().stream().anyMatch(e -> blank(e.getKey()) || !ed25519(e.getValue())) || blank(issuer) || blank(audience) || clock == null) throw new IllegalArgumentException("access token configuration invalid");
        this.privateKey=null; this.publicKeys=Map.copyOf(publicKeys); this.kid=null; this.issuer=issuer; this.audience=audience; this.ttl=null; this.clock=clock;
    }
    public String issue(UUID userId) {
        if (privateKey == null || userId == null) throw new IllegalStateException("access token issuer unavailable");
        Instant now=clock.instant();
        return Jwts.builder().header().keyId(kid).and().subject(userId.toString()).issuer(issuer).audience().add(audience).and().issuedAt(Date.from(now)).expiration(Date.from(now.plus(ttl))).signWith(privateKey, Jwts.SIG.EdDSA).compact();
    }
    public AccessTokenClaims verify(String token) {
        try {
            Jws<Claims> jws = Jwts.parser().clock(() -> Date.from(clock.instant())).keyLocator(header -> {
                Object keyId = header.get("kid");
                return keyId instanceof String value && !blank(value) ? publicKeys.get(value) : null;
            }).build().parseSignedClaims(token);
            Claims claims=jws.getPayload();
            if (!"EdDSA".equals(jws.getHeader().getAlgorithm()) || !issuer.equals(claims.getIssuer()) || !claims.getAudience().contains(audience) || claims.getIssuedAt()==null || claims.getExpiration()==null || !claims.getExpiration().toInstant().isAfter(clock.instant())) throw new TokenVerificationException("access token invalid");
            return new AccessTokenClaims(UUID.fromString(claims.getSubject()), claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant());
        } catch (TokenVerificationException e) { throw e; } catch (Exception e) { throw new TokenVerificationException("access token invalid"); }
    }
    private static boolean blank(String v) { return v == null || v.isBlank(); }
    private static boolean ed25519(Object key) { return key instanceof EdECKey edEcKey && "Ed25519".equals(edEcKey.getParams().getName()); }
}
