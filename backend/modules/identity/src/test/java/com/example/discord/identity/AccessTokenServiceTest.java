package com.example.discord.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {
    @Test
    void issuesAndVerifiesSignedAccessToken() {
        UUID userId = UUID.randomUUID();
        Clock clock = Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC);
        AccessTokenService service = new AccessTokenService("test-secret-with-enough-length", Duration.ofHours(1), clock);

        String token = service.issue(userId);
        AccessTokenClaims claims = service.verify(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.issuedAt()).isEqualTo(Instant.parse("2026-05-13T00:00:00Z"));
        assertThat(claims.expiresAt()).isEqualTo(Instant.parse("2026-05-13T01:00:00Z"));
    }

    @Test
    void rejectsExpiredAccessToken() {
        UUID userId = UUID.randomUUID();
        AccessTokenService issuer = new AccessTokenService(
            "test-secret-with-enough-length",
            Duration.ofMinutes(5),
            Clock.fixed(Instant.parse("2026-05-13T00:00:00Z"), ZoneOffset.UTC)
        );
        String token = issuer.issue(userId);
        AccessTokenService verifier = new AccessTokenService(
            "test-secret-with-enough-length",
            Duration.ofMinutes(5),
            Clock.fixed(Instant.parse("2026-05-13T00:06:00Z"), ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> verifier.verify(token))
            .isInstanceOf(TokenVerificationException.class)
            .hasMessage("access token expired");
    }
}
