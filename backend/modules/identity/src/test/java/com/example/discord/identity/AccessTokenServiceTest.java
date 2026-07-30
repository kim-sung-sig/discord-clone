package com.example.discord.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.Signature;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class AccessTokenServiceTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-14T00:00:00Z"), ZoneOffset.UTC);
    private static final String KID = "identity-2026-07";
    private static final String ISSUER = "discord-identity";
    private static final String AUDIENCE = "discord-api";

    @Test
    void issuesAndVerifiesEdDsaWithPkcs8AndX509PemOnJava21() throws Exception {
        PrivateKey privateKey = privateKey(PemLocationReader.read("classpath:/identity/ed25519-private.pem"));
        PublicKey publicKey = publicKey(PemLocationReader.read("classpath:/identity/ed25519-public.pem"));
        UUID userId = UUID.randomUUID();
        AccessTokenService issuer = issuer(privateKey);
        AccessTokenService verifier = new AccessTokenService(Map.of(KID, publicKey), ISSUER, AUDIENCE, CLOCK);

        String token = issuer.issue(userId);
        AccessTokenClaims claims = verifier.verify(token);

        assertThat(token.split("\\.")[0]).doesNotContain("UlMyNTY");
        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.issuedAt()).isEqualTo(CLOCK.instant());
        assertThat(claims.expiresAt()).isEqualTo(CLOCK.instant().plus(Duration.ofHours(1)));
    }

    @Test
    void rejectsMissingAndUnsupportedPemLocations() {
        assertThatThrownBy(() -> PemLocationReader.read("classpath:/identity/missing.pem")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PemLocationReader.read("https://keys.example/key.pem")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void readsClasspathRootPemWithOrWithoutLeadingSlash() {
        assertThat(PemLocationReader.read("classpath:identity/ed25519-public.pem"))
            .isEqualTo(PemLocationReader.read("classpath:/identity/ed25519-public.pem"));
    }

    @Test
    void rejectsWrongAlgorithmAndMissingOrUnknownKid() throws Exception {
        KeyPair keys = keys();
        AccessTokenService verifier = verifier(keys);
        UUID userId = UUID.randomUUID();

        assertRejected(verifier, hs256Token(userId));
        assertRejected(verifier, signedToken(keys.getPrivate(), "EdDSA", null, ISSUER, AUDIENCE, userId, CLOCK.instant(), CLOCK.instant().plusSeconds(60)));
        assertRejected(verifier, signedToken(keys.getPrivate(), "EdDSA", "", ISSUER, AUDIENCE, userId, CLOCK.instant(), CLOCK.instant().plusSeconds(60)));
        assertRejected(verifier, signedToken(keys.getPrivate(), "EdDSA", "unknown", ISSUER, AUDIENCE, userId, CLOCK.instant(), CLOCK.instant().plusSeconds(60)));
    }

    @Test
    void rejectsNoneRsEsAndOmittedAlgorithms() throws Exception {
        KeyPair keys = keys();
        AccessTokenService verifier = verifier(keys);
        Instant now = CLOCK.instant();

        assertRejected(verifier, signedToken(keys.getPrivate(), "none", KID, ISSUER, AUDIENCE, UUID.randomUUID(), now, now.plusSeconds(60)));
        assertRejected(verifier, signedToken(keys.getPrivate(), "RS256", KID, ISSUER, AUDIENCE, UUID.randomUUID(), now, now.plusSeconds(60)));
        assertRejected(verifier, signedToken(keys.getPrivate(), "ES256", KID, ISSUER, AUDIENCE, UUID.randomUUID(), now, now.plusSeconds(60)));
        assertRejected(verifier, signedToken(keys.getPrivate(), null, KID, ISSUER, AUDIENCE, UUID.randomUUID(), now, now.plusSeconds(60)));
    }

    @Test
    void rejectsEd448Keys() throws Exception {
        KeyPair keys = KeyPairGenerator.getInstance("Ed448").generateKeyPair();

        assertThatThrownBy(() -> new AccessTokenService(keys.getPrivate(), KID, ISSUER, AUDIENCE, Duration.ofHours(1), CLOCK))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("access token configuration invalid");
        assertThatThrownBy(() -> new AccessTokenService(Map.of(KID, keys.getPublic()), ISSUER, AUDIENCE, CLOCK))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("access token configuration invalid");
    }

    @Test
    void rejectsInvalidClaimsSignatureAndSubject() throws Exception {
        KeyPair keys = keys();
        AccessTokenService verifier = verifier(keys);
        Instant now = CLOCK.instant();

        assertRejected(verifier, signedToken(keys.getPrivate(), "EdDSA", KID, "other", AUDIENCE, UUID.randomUUID(), now, now.plusSeconds(60)));
        assertRejected(verifier, signedToken(keys.getPrivate(), "EdDSA", KID, ISSUER, "other", UUID.randomUUID(), now, now.plusSeconds(60)));
        assertRejected(verifier, signedToken(keys.getPrivate(), "EdDSA", KID, ISSUER, AUDIENCE, UUID.randomUUID(), now, now));
        assertRejected(verifier, signedToken(keys.getPrivate(), "EdDSA", KID, ISSUER, AUDIENCE, "not-a-uuid", now, now.plusSeconds(60)));
        assertRejected(verifier, issuer(keys.getPrivate()).issue(UUID.randomUUID()) + "x");
    }

    private static AccessTokenService issuer(PrivateKey privateKey) {
        return new AccessTokenService(privateKey, KID, ISSUER, AUDIENCE, Duration.ofHours(1), CLOCK);
    }

    private static AccessTokenService verifier(KeyPair keys) {
        return new AccessTokenService(Map.of(KID, keys.getPublic()), ISSUER, AUDIENCE, CLOCK);
    }

    private static void assertRejected(AccessTokenService verifier, String token) {
        assertThatThrownBy(() -> verifier.verify(token)).isInstanceOf(TokenVerificationException.class)
            .hasMessage("access token invalid");
    }

    private static KeyPair keys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        return generator.generateKeyPair();
    }
    private static PrivateKey privateKey(String pem) throws Exception { return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getMimeDecoder().decode(pem.replaceAll("-----[^-]+-----|\\s", "")))); }
    private static PublicKey publicKey(String pem) throws Exception { return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getMimeDecoder().decode(pem.replaceAll("-----[^-]+-----|\\s", "")))); }

    private static String hs256Token(UUID userId) throws Exception {
        String header = encoded("{\"alg\":\"HS256\",\"kid\":\"" + KID + "\"}");
        String payload = encoded("{\"sub\":\"" + userId + "\",\"iss\":\"" + ISSUER + "\",\"aud\":[\"" + AUDIENCE + "\"],\"iat\":" + CLOCK.instant().getEpochSecond() + ",\"exp\":" + CLOCK.instant().plusSeconds(60).getEpochSecond() + "}");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("test-secret-with-enough-length".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal((header + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }

    private static String signedToken(PrivateKey key, String algorithm, String kid, String issuer, String audience, Object subject, Instant issuedAt, Instant expiresAt) throws Exception {
        String header = encoded("{" + (algorithm == null ? "" : "\"alg\":\"" + algorithm + "\"") + (kid == null ? "" : (algorithm == null ? "\"kid\":\"" : ",\"kid\":\"") + kid + "\"") + "}");
        String payload = encoded("{\"sub\":\"" + subject + "\",\"iss\":\"" + issuer + "\",\"aud\":[\"" + audience + "\"],\"iat\":" + issuedAt.getEpochSecond() + ",\"exp\":" + expiresAt.getEpochSecond() + "}");
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update((header + "." + payload).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(signer.sign());
    }

    private static String encoded(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
