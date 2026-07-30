package com.example.discord.identityservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.discord.identity.AccessTokenService;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class IdentityServiceApplicationTest {
    @DynamicPropertySource
    static void tokenProperties(DynamicPropertyRegistry registry) {
        registry.add("discord.auth.jwt.issuer", () -> "discord-identity");
        registry.add("discord.auth.jwt.audience", () -> "discord-api");
        registry.add("discord.auth.jwt.key-id", () -> "identity-2026-07");
        registry.add("discord.auth.jwt.private-key-location", () -> keyFixture("ed25519-private.pem"));
        registry.add("discord.auth.jwt.public-key-locations.identity-2026-07", () -> keyFixture("ed25519-public.pem"));
    }

    private static String keyFixture(String name) {
        return "file:" + Path.of("..", "..", "modules", "identity", "src", "test", "resources", "identity", name).toAbsolutePath();
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private AccessTokenService accessTokens;

    @Test
    void exposesHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void profileAcceptsSelfIssuedEdDsaTokenWithMatchingPublicKeyMap() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = accessTokens.issue(userId);

        mockMvc.perform(get("/api/users/@me").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).contains(userId.toString()));
    }

    @Test
    void profileRejectsMissingOrInvalidBearerToken() throws Exception {
        mockMvc.perform(get("/api/users/@me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/users/@me").header("Authorization", "Bearer malformed"))
            .andExpect(status().isUnauthorized());
    }
}
