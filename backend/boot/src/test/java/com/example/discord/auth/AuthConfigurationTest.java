package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.BearerTokenVerifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
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

    @Test
    void applicationImportsJwtPropertiesFromMountedConfigTree(@TempDir Path configTree) throws IOException {
        Files.createDirectories(configTree.resolve("discord/auth/jwt/public-key-locations"));
        Files.writeString(configTree.resolve("discord/auth/jwt/issuer"), "discord-identity");
        Files.writeString(configTree.resolve("discord/auth/jwt/audience"), "discord-api");
        Files.writeString(configTree.resolve("discord/auth/jwt/key-id"), "identity-2026-07");
        Path publicKey = configTree.resolve("identity/ed25519-public.pem");
        Files.createDirectories(publicKey.getParent());
        Files.copy(Path.of("..", "modules", "identity", "src", "test", "resources", "identity", "ed25519-public.pem"), publicKey);
        Files.writeString(configTree.resolve("discord/auth/jwt/public-key-locations/identity-2026-07"), "file:" + publicKey.toAbsolutePath());

        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AuthConfiguration.class)
            .web(WebApplicationType.NONE)
            .properties("spring.config.location=" + Path.of("src", "main", "resources", "application.yml").toAbsolutePath().toUri())
            .properties("DISCORD_JWT_CONFIG_TREE=" + configTree.toAbsolutePath() + "/")
            .run()) {
            assertThat(context.getBean(AuthConfiguration.JwtProperties.class))
                .extracting(AuthConfiguration.JwtProperties::issuer, AuthConfiguration.JwtProperties::audience, AuthConfiguration.JwtProperties::keyId)
                .containsExactly("discord-identity", "discord-api", "identity-2026-07");
            assertThat(context.getBeansOfType(BearerTokenVerifier.class)).hasSize(1);
        }
    }

    private static String keyFixture() {
        return "file:" + Path.of("..", "modules", "identity", "src", "test", "resources", "identity", "ed25519-public.pem").toAbsolutePath();
    }
}
