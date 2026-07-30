package com.example.discord.websocketservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.discord.identity.BearerTokenVerifier;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BearerTokenVerifierTest {
    @Test
    void verifiesValidEdDsaTokenLocally() {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        assertThat(verifier().requireUserId("Bearer " + validToken())).isEqualTo(userId);
    }

    @Test
    void rejectsMalformedTokenLocally() {
        assertThatThrownBy(() -> verifier().requireUserId("Bearer malformed")).isInstanceOf(IllegalArgumentException.class);
    }

    private static BearerTokenVerifier verifier() { return new BearerTokenVerifier(Map.of("identity-2026-07", publicKey()), "discord-identity", "discord-api", Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC)); }
    private static String validToken() { return resource("/identity/valid-eddsa-token.jwt"); }
    private static PublicKey publicKey() { try { return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(resource("/identity/ed25519-public.pem").replaceAll("-----[^-]+-----|\\s", "")))); } catch (Exception exception) { throw new AssertionError(exception); } }
    private static String resource(String path) { try (var stream = BearerTokenVerifierTest.class.getResourceAsStream(path)) { if (stream == null) throw new AssertionError("test fixture missing"); return new String(stream.readAllBytes()).trim(); } catch (java.io.IOException exception) { throw new AssertionError(exception); } }
}
