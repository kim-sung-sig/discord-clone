package com.example.discord.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BearerTokenVerifierTest {
    @Test
    void returnsUuidFromValidEdDsaBearerToken() throws Exception {
        KeyPair keys = keys();
        Clock clock = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
        UUID userId = UUID.randomUUID();
        String token = new AccessTokenService(keys.getPrivate(), "key-1", "discord-identity", "discord-api", Duration.ofHours(1), clock).issue(userId);
        BearerTokenVerifier verifier = new BearerTokenVerifier(Map.of("key-1", keys.getPublic()), "discord-identity", "discord-api", clock);

        assertThat(verifier.requireUserId("Bearer " + token)).isEqualTo(userId);
    }

    @Test
    void rejectsMalformedAuthorizationWithoutExposingIt() throws Exception {
        KeyPair keys = keys();
        BearerTokenVerifier verifier = new BearerTokenVerifier(Map.of("key-1", keys.getPublic()), "discord-identity", "discord-api", Clock.systemUTC());

        assertThatThrownBy(() -> verifier.requireUserId("Bearer malformed-token"))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("access token invalid");
    }

    private static KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        return generator.generateKeyPair();
    }
}
