package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.BearerTokenVerifier;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AuthConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(AuthConfiguration.class)
        .withPropertyValues(
            "spring.profiles.active=production",
            "discord.auth.jwt.issuer=discord-identity",
            "discord.auth.jwt.audience=discord-api",
            "discord.auth.jwt.key-id=identity-2026-07",
            "discord.auth.jwt.public-key-locations.identity-2026-07=" + keyFixture()
        );

    @Test
    void productionUsesOnlyTheIdentityPublicKeyVerifier() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(BearerTokenVerifier.class);
            assertThat(context).doesNotHaveBean(AccessTokenService.class);
        });
    }

    private static String keyFixture() {
        return "file:" + Path.of("..", "modules", "identity", "src", "test", "resources", "identity", "ed25519-public.pem").toAbsolutePath();
    }
}
