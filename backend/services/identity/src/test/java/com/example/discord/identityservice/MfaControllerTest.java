package com.example.discord.identityservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.example.discord.identity.AccessTokenService;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MfaControllerTest {
    @Test
    void recoveryRequiresFreshExactPasskeyOriginAndLoopback() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(new String(getClass().getResourceAsStream("/db/mfa-schema.sql").readAllBytes(), StandardCharsets.UTF_8));
        }
        var store = new JdbcTotpStore(dataSource, new SecretCipher(new byte[32]));
        UUID owner = UUID.randomUUID();
        byte[] oldSecret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        Instant now = Instant.ofEpochSecond(1_111_111_109);
        store.enroll(owner, oldSecret, now.plusSeconds(300));
        store.verify(owner, "287082", Instant.ofEpochSecond(59), true);
        String wrong = wrongAuthenticatorCode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", now);
        for (int attempt = 0; attempt < 5; attempt++) store.verify(owner, wrong, now, false);
        var current = new java.util.concurrent.atomic.AtomicReference<>(now);
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return current.get(); }
        };
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var tokens = new AccessTokenService(keys.getPrivate(), Map.of("test", keys.getPublic()), "test", "issuer", "api", Duration.ofHours(1), clock);
        var mvc = MockMvcBuilders.standaloneSetup(new MfaController(store, tokens, clock, "http://localhost:8080")).build();
        String endpoint = "/api/auth/mfa/totp/unlock";
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + tokens.issue(owner)).header("Origin", "http://localhost:8080"))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + tokens.issueMfa(owner, "totp")).header("Origin", "http://localhost:8080"))
            .andExpect(status().isForbidden());
        String passkey = "Bearer " + tokens.issueMfa(owner, "passkey");
        mvc.perform(post(endpoint).header("Authorization", passkey)).andExpect(status().isForbidden());
        mvc.perform(post(endpoint).header("Authorization", passkey).header("Origin", "http://127.0.0.1:8080"))
            .andExpect(status().isForbidden());
        mvc.perform(post(endpoint).header("Authorization", passkey).header("Origin", "http://localhost:8080")
                .with(request -> { request.setRemoteAddr("10.0.0.1"); return request; }))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/mfa/totp/reset").header("Authorization", passkey).header("Origin", "http://localhost:8080"))
            .andExpect(status().isConflict());
        mvc.perform(post(endpoint).header("Authorization", passkey).header("Origin", "http://localhost:8080"))
            .andExpect(status().isNoContent());
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT failures FROM mfa_totp WHERE user_id='" + owner + "'")) {
            org.junit.jupiter.api.Assertions.assertTrue(rows.next());
            org.junit.jupiter.api.Assertions.assertEquals(0, rows.getInt(1));
        }
        String reset = mvc.perform(post("/api/auth/mfa/totp/reset").header("Authorization", passkey).header("Origin", "http://localhost:8080"))
            .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsString();
        String secret = com.jayway.jsonpath.JsonPath.read(reset, "$.secret");
        mvc.perform(post("/api/auth/mfa/totp/confirm").header("Authorization", "Bearer " + tokens.issue(owner))
                .contentType("application/json").content("{\"code\":\"" + authenticatorCode(secret, now) + "\"}"))
            .andExpect(status().isNoContent());
        var unavailable = new JdbcTotpStore(new org.springframework.jdbc.datasource.AbstractDataSource() {
            @Override public java.sql.Connection getConnection() throws java.sql.SQLException { throw new java.sql.SQLException("offline"); }
            @Override public java.sql.Connection getConnection(String user, String password) throws java.sql.SQLException { throw new java.sql.SQLException("offline"); }
        }, new SecretCipher(new byte[32]));
        var unavailableMvc = MockMvcBuilders.standaloneSetup(new MfaController(unavailable, tokens, clock, "http://localhost:8080")).build();
        unavailableMvc.perform(post(endpoint).header("Authorization", passkey).header("Origin", "http://localhost:8080"))
            .andExpect(status().isServiceUnavailable());
        String stale = "Bearer " + tokens.issueMfa(owner, "passkey");
        current.set(now.plusSeconds(301));
        mvc.perform(post(endpoint).header("Authorization", stale).header("Origin", "http://localhost:8080"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void enrollsConfirmsAndStepsUpThroughHttpWithoutReplacingAnExistingSecret() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(new String(getClass().getResourceAsStream("/db/mfa-schema.sql").readAllBytes(), StandardCharsets.UTF_8));
        }
        var store = new JdbcTotpStore(dataSource, new SecretCipher(new byte[32]));
        var time = new java.util.concurrent.atomic.AtomicReference<>(Instant.parse("2026-09-14T00:00:00Z"));
        Clock clock = new Clock() {
            public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return time.get(); }
        };
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var tokens = new AccessTokenService(keys.getPrivate(), Map.of("test", keys.getPublic()), "test", "issuer", "api", Duration.ofHours(1), clock);
        var mvc = MockMvcBuilders.standaloneSetup(new MfaController(store, tokens, clock, "http://localhost:8080")).build();
        String bearer = "Bearer " + tokens.issue(UUID.randomUUID());
        String enrollment = mvc.perform(post("/api/auth/mfa/totp/enroll").header("Authorization", bearer))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
            .andReturn().getResponse().getContentAsString();
        String secret = com.jayway.jsonpath.JsonPath.read(enrollment, "$.secret");
        mvc.perform(post("/api/auth/mfa/totp/enroll").header("Authorization", bearer)).andExpect(status().isConflict());
        mvc.perform(post("/api/auth/mfa/totp/challenges").header("Authorization", bearer)).andExpect(status().isConflict());
        String code = authenticatorCode(secret, time.get());
        mvc.perform(post("/api/auth/mfa/totp/confirm").header("Authorization", bearer)
            .contentType("application/json").content("{\"code\":\"" + code + "\"}"))
            .andExpect(status().isNoContent());
        String response = mvc.perform(post("/api/auth/mfa/totp/challenges").header("Authorization", bearer))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(response, "$.challengeId");
        time.updateAndGet(now -> now.plusSeconds(30));
        String completed = mvc.perform(post("/api/auth/mfa/totp/challenges/" + id + "/complete").header("Authorization", bearer)
            .contentType("application/json").content("{\"code\":\"" + authenticatorCode(secret, time.get()) + "\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = com.jayway.jsonpath.JsonPath.read(completed, "$.accessToken");
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("totp"), tokens.verify(access).amr());
        org.junit.jupiter.api.Assertions.assertEquals(time.get().plusSeconds(300), tokens.verify(access).expiresAt());
    }

    private static String authenticatorCode(String base32, Instant now) throws Exception {
        java.math.BigInteger value = java.math.BigInteger.ZERO;
        for (char character : base32.toCharArray()) {
            value = value.shiftLeft(5).or(java.math.BigInteger.valueOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character)));
        }
        byte[] encoded = value.toByteArray();
        byte[] secret = new byte[20];
        int count = Math.min(encoded.length, secret.length);
        System.arraycopy(encoded, encoded.length - count, secret, secret.length - count, count);
        var mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA1"));
        byte[] digest = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(now.getEpochSecond() / 30).array());
        int offset = digest[19] & 15;
        int binary = java.nio.ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
        return String.format(java.util.Locale.ROOT, "%06d", binary % 1_000_000);
    }

    private static String wrongAuthenticatorCode(String base32, Instant now) throws Exception {
        var accepted = new java.util.HashSet<String>();
        for (int offset : new int[] {-30, 0, 30}) accepted.add(authenticatorCode(base32, now.plusSeconds(offset)));
        String candidate = "000000";
        while (accepted.contains(candidate)) candidate = String.format(java.util.Locale.ROOT, "%06d", (Integer.parseInt(candidate) + 1) % 1_000_000);
        return candidate;
    }

    @Test
    void requiresBearerAndConsumesOnlyTheOwnersChallenge() throws Exception {
        var dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(new String(getClass().getResourceAsStream("/db/mfa-schema.sql").readAllBytes(), StandardCharsets.UTF_8));
        }
        var store = new JdbcTotpStore(dataSource, new SecretCipher(new byte[32]));
        UUID owner = UUID.randomUUID();
        store.enroll(owner, "12345678901234567890".getBytes(StandardCharsets.US_ASCII), Instant.ofEpochSecond(300));
        store.verify(owner, "287082", Instant.ofEpochSecond(59), true);
        Instant now = Instant.ofEpochSecond(1_111_111_109);
        var clock = Clock.fixed(now, ZoneOffset.UTC);
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var tokens = new AccessTokenService(keys.getPrivate(), Map.of("test", keys.getPublic()), "test", "issuer", "api", Duration.ofHours(1), clock);
        var mvc = MockMvcBuilders.standaloneSetup(new MfaController(store, tokens, clock, "http://localhost:8080")).build();
        mvc.perform(post("/api/auth/mfa/totp/challenges")).andExpect(status().isUnauthorized());
        UUID challenge = store.challenge(owner, now);
        String endpoint = "/api/auth/mfa/totp/challenges/" + challenge + "/complete";
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + tokens.issue(UUID.randomUUID()))
            .contentType("application/json").content("{\"code\":\"081804\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + tokens.issue(owner))
            .contentType("application/json").content("{\"code\":\"081804\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
        mvc.perform(post(endpoint).header("Authorization", "Bearer " + tokens.issue(owner))
            .contentType("application/json").content("{\"code\":\"081804\"}"))
            .andExpect(status().isUnauthorized());
    }
}
