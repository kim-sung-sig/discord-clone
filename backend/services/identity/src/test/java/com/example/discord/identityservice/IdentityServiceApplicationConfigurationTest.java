package com.example.discord.identityservice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IdentityServiceApplicationConfigurationTest {
    @Test
    void rejectsClasspathPrivateKeyLocation() {
        var properties = new IdentityServiceApplication.JwtProperties(
            "discord-identity",
            "discord-api",
            "identity-2026-07",
            "classpath:identity/ed25519-private.pem",
            Map.of("identity-2026-07", "classpath:identity/ed25519-public.pem")
        );

        assertThatThrownBy(() -> new IdentityServiceApplication().accessTokenService(properties, Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("access token configuration invalid");
    }

    @Test
    void rejectsBlankRequiredJwtProperties() {
        assertRejected("", "discord-api", "identity-2026-07", "file:key.pem", Map.of("identity-2026-07", "classpath:identity/ed25519-public.pem"));
        assertRejected("discord-identity", "", "identity-2026-07", "file:key.pem", Map.of("identity-2026-07", "classpath:identity/ed25519-public.pem"));
        assertRejected("discord-identity", "discord-api", "", "file:key.pem", Map.of("identity-2026-07", "classpath:identity/ed25519-public.pem"));
        assertRejected("discord-identity", "discord-api", "identity-2026-07", "", Map.of("identity-2026-07", "classpath:identity/ed25519-public.pem"));
        assertRejected("discord-identity", "discord-api", "identity-2026-07", "file:key.pem", Map.of("identity-2026-07", ""));
    }

    @Test
    void rejectsMalformedPrivateAndPublicPem() {
        assertRejected("discord-identity", "discord-api", "identity-2026-07", fixture("malformed-private.pem"), Map.of("identity-2026-07", fixture("ed25519-public.pem")));
        assertRejected("discord-identity", "discord-api", "identity-2026-07", fixture("ed25519-private.pem"), Map.of("identity-2026-07", fixture("malformed-public.pem")));
    }

    private static void assertRejected(String issuer, String audience, String keyId, String privateKeyLocation, Map<String, String> publicKeyLocations) {
        var properties = new IdentityServiceApplication.JwtProperties(issuer, audience, keyId, privateKeyLocation, publicKeyLocations);
        assertThatThrownBy(() -> new IdentityServiceApplication().accessTokenService(properties, Clock.systemUTC()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("access token configuration invalid");
    }

    private static String fixture(String name) {
        return "file:" + Path.of("..", "..", "modules", "identity", "src", "test", "resources", "identity", name).toAbsolutePath();
    }
}
