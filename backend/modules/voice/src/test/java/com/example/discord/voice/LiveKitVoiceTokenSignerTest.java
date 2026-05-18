package com.example.discord.voice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveKitVoiceTokenSignerTest {
    private static final UUID GUILD_ID = UUID.fromString("00000000-0000-0000-0000-000000000201");
    private static final UUID CHANNEL_ID = UUID.fromString("00000000-0000-0000-0000-000000000202");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000203");
    private static final Instant NOW = Instant.parse("2026-05-17T00:00:00Z");

    @Test
    void signsLiveKitRoomTokenWithScopedClaimsWithoutExposingSecret() {
        LiveKitVoiceTokenSigner signer = new LiveKitVoiceTokenSigner(
            "lk-api-key",
            "production-secret-value-at-least-32-characters"
        );

        VoiceRoomToken token = signer.sign(new VoiceTokenSigningRequest(
            GUILD_ID,
            CHANNEL_ID,
            USER_ID,
            NOW,
            900L
        ));

        assertThat(token.provider()).isEqualTo(VoiceRoomToken.LIVEKIT_PROVIDER);
        assertThat(token.room()).isEqualTo("guild:%s:voice:%s".formatted(GUILD_ID, CHANNEL_ID));
        assertThat(token.participant()).isEqualTo("user:%s".formatted(USER_ID));
        assertThat(token.expiresAt()).isEqualTo(NOW.plusSeconds(900));
        assertThat(token.token()).doesNotContain("production-secret-value");

        String[] jwtParts = token.token().split("\\.");
        assertThat(jwtParts).hasSize(3);
        assertThat(json(jwtParts[0])).contains("\"alg\":\"HS256\"");
        assertThat(json(jwtParts[0])).contains("\"typ\":\"JWT\"");

        String payload = json(jwtParts[1]);
        assertThat(payload).contains("\"iss\":\"lk-api-key\"");
        assertThat(payload).contains("\"sub\":\"user:%s\"".formatted(USER_ID));
        assertThat(payload).contains("\"exp\":1778976900");
        assertThat(payload).contains("\"nbf\":1778976000");
        assertThat(payload).contains("\"roomJoin\":true");
        assertThat(payload).contains("\"room\":\"guild:%s:voice:%s\"".formatted(GUILD_ID, CHANNEL_ID));
        assertThat(payload).contains("\"guildId\":\"%s\"".formatted(GUILD_ID));
        assertThat(payload).contains("\"channelId\":\"%s\"".formatted(CHANNEL_ID));
        assertThat(payload).contains("\"userId\":\"%s\"".formatted(USER_ID));
    }

    private static String json(String base64Url) {
        return new String(Base64.getUrlDecoder().decode(base64Url), StandardCharsets.UTF_8);
    }
}
