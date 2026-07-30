package com.example.discord.websocketservice;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WebsocketServiceApplicationTest {
    @DynamicPropertySource
    static void tokenProperties(DynamicPropertyRegistry registry) {
        registry.add("discord.auth.jwt.issuer", () -> "discord-identity");
        registry.add("discord.auth.jwt.audience", () -> "discord-api");
        registry.add("discord.auth.jwt.key-id", () -> "identity-2026-07");
        registry.add("discord.auth.jwt.public-key-locations.identity-2026-07", () -> "classpath:identity/ed25519-public.pem");
    }
    @Autowired
    private MockMvc mockMvc;

    @Test
    void exposesHealth() throws Exception {
        mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void rejectsBlankAndMalformedPublicKeyConfiguration() {
        assertThatThrownBy(() -> new WebsocketServiceApplication().bearerTokenVerifier(new WebsocketServiceApplication.JwtProperties("discord-identity", "", "key-1", Map.of("key-1", "classpath:identity/ed25519-public.pem")), Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new WebsocketServiceApplication().bearerTokenVerifier(new WebsocketServiceApplication.JwtProperties("discord-identity", "discord-api", "key-1", Map.of("key-1", "classpath:identity/missing.pem")), Clock.systemUTC())).isInstanceOf(IllegalStateException.class);
    }

}
