package com.example.discord.identityservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.Clock;
import java.time.Duration;
import java.security.KeyPairGenerator;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.example.discord.identity.AccessTokenService;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
@Testcontainers
class PostgresMfaRecoveryPersistenceTest {
    private static final String ORIGIN = "http://localhost:8080";
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void pendingTotpRotationAndUnlockPersistAcrossStoreInstances() {
        DataSource source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(source);
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(source);
        UUID owner = UUID.randomUUID();
        byte[] oldSecret = "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] newSecret = "abcdefghijklmnopqrst".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var first = new JdbcTotpStore(source, new SecretCipher(new byte[32]));
        Instant start = Instant.ofEpochSecond(1_111_111_109);
        assertTrue(first.enroll(owner, oldSecret, start.plusSeconds(300)));
        assertTrue(first.verify(owner, "287082", Instant.ofEpochSecond(59), true));
        first.reset(owner, newSecret, start.plusSeconds(300));
        var afterRestart = new JdbcTotpStore(source, new SecretCipher(new byte[32]));
        assertTrue(afterRestart.verify(owner, code(oldSecret, start), start, false));
        assertTrue(afterRestart.verify(owner, code(newSecret, start.plusSeconds(30)), start.plusSeconds(30), true));
        String wrong = wrongCode(newSecret, start.plusSeconds(60));
        for (int attempt = 0; attempt < 5; attempt++) assertFalse(afterRestart.verify(owner, wrong, start.plusSeconds(60), false));
        new JdbcTotpStore(source, new SecretCipher(new byte[32])).unlock(owner);
        assertTrue(afterRestart.verify(owner, code(newSecret, start.plusSeconds(90)), start.plusSeconds(90), false));
    }

    @Test
    void lastPasskeyDeletionRequiresActiveUnlockedTotpOnPostgres() throws Exception {
        DataSource source = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(source);
        UUID owner = UUID.randomUUID();
        var totp = new JdbcTotpStore(source, new SecretCipher(new byte[32]));
        assertTrue(totp.enroll(owner, "12345678901234567890".getBytes(java.nio.charset.StandardCharsets.US_ASCII), Instant.now().plusSeconds(300)));
        assertTrue(totp.verify(owner, "287082", Instant.ofEpochSecond(59), true));
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var clock = Clock.systemUTC();
        var tokens = new AccessTokenService(keys.getPrivate(), Map.of("test", keys.getPublic()), "test", "issuer", "api", Duration.ofHours(1), clock);
        var config = new WebAuthnConfiguration();
        var users = config.webAuthnUsers(source);
        var credentials = config.webAuthnCredentials(source);
        var environment = new MockEnvironment().withProperty("server.address", "127.0.0.1").withProperty("discord.auth.webauthn.origin", ORIGIN);
        var operations = config.webAuthnOperations(environment, users, credentials);
        var mvc = MockMvcBuilders.standaloneSetup(new WebAuthnController(tokens, clock, operations, users, credentials, source, ORIGIN)).build();
        String credentialId = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        byte[] publicKey = KeyPairGenerator.getInstance("EC").generateKeyPair().getPublic().getEncoded();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO user_entities (id, name, display_name) VALUES (?, ?, ?)", owner.toString(), owner.toString(), "Test user");
        jdbc.update("INSERT INTO user_credentials (credential_id, user_entity_user_id, public_key, signature_count, uv_initialized, backup_eligible, backup_state, public_key_credential_type, authenticator_transports, created, last_used, label) VALUES (?, ?, ?, 0, TRUE, FALSE, FALSE, 'public-key', 'internal', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'Test passkey')",
                credentialId, owner.toString(), publicKey);
        jdbc.update("UPDATE mfa_totp SET failures = 5 WHERE user_id = ?", owner.toString());
        mvc.perform(delete("/api/auth/mfa/webauthn/credentials/" + credentialId).header("Authorization", "Bearer " + tokens.issueMfa(owner, "totp"))
                .header("Origin", ORIGIN)).andExpect(status().isConflict());
        jdbc.update("UPDATE mfa_totp SET failures = 0, active = FALSE WHERE user_id = ?", owner.toString());
        mvc.perform(delete("/api/auth/mfa/webauthn/credentials/" + credentialId).header("Authorization", "Bearer " + tokens.issueMfa(owner, "totp"))
                .header("Origin", ORIGIN)).andExpect(status().isConflict());
        jdbc.update("UPDATE mfa_totp SET active = TRUE, failures = 4 WHERE user_id = ?", owner.toString());
        mvc.perform(delete("/api/auth/mfa/webauthn/credentials/" + credentialId).header("Authorization", "Bearer " + tokens.issueMfa(owner, "totp"))
                .header("Origin", ORIGIN)).andExpect(status().isNoContent());
    }

    private static String code(byte[] secret, Instant now) {
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA1"));
            byte[] digest = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(now.getEpochSecond() / 30).array());
            int offset = digest[19] & 15;
            int value = java.nio.ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
            return String.format(java.util.Locale.ROOT, "%06d", value % 1_000_000);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String wrongCode(byte[] secret, Instant now) {
        try {
            var accepted = java.util.Set.of(code(secret, now.minusSeconds(30)), code(secret, now), code(secret, now.plusSeconds(30)));
            String candidate = "000000";
            while (accepted.contains(candidate)) candidate = String.format(java.util.Locale.ROOT, "%06d", (Integer.parseInt(candidate) + 1) % 1_000_000);
            return candidate;
        } catch (RuntimeException exception) {
            throw exception;
        }
    }
}
