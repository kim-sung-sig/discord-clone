package com.example.discord.identityservice;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

class JdbcTotpStoreTest {
    @Test
    void concurrentPendingConfirmationRotatesExactlyOnce() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(new String(getClass().getResourceAsStream("/db/mfa-schema.sql").readAllBytes(), StandardCharsets.UTF_8));
        }
        byte[] oldSecret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        byte[] newSecret = "abcdefghijklmnopqrst".getBytes(StandardCharsets.US_ASCII);
        var first = new JdbcTotpStore(dataSource, new SecretCipher(new byte[32]));
        var second = new JdbcTotpStore(dataSource, new SecretCipher(new byte[32]));
        UUID owner = UUID.randomUUID();
        Instant now = Instant.ofEpochSecond(1_111_111_109);
        first.enroll(owner, oldSecret, now.plusSeconds(300));
        assertTrue(first.verify(owner, "287082", Instant.ofEpochSecond(59), true));
        first.reset(owner, newSecret, now.plusSeconds(300));
        String code = code(newSecret, now);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> first.verify(owner, code, now, true));
            var b = executor.submit(() -> second.verify(owner, code, now, true));
            assertNotEquals(a.get(), b.get());
        }
    }

    @Test
    void pendingResetKeepsOldSecretUntilConfirmationAndExpiresSafely() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(new String(getClass().getResourceAsStream("/db/mfa-schema.sql").readAllBytes(), StandardCharsets.UTF_8));
        }
        var store = new JdbcTotpStore(dataSource, new SecretCipher(new byte[32]));
        UUID owner = UUID.randomUUID();
        byte[] oldSecret = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
        byte[] newSecret = "abcdefghijklmnopqrst".getBytes(StandardCharsets.US_ASCII);
        Instant now = Instant.ofEpochSecond(1_111_111_109);
        store.enroll(owner, oldSecret, now.plusSeconds(300));
        assertTrue(store.verify(owner, code(oldSecret, Instant.ofEpochSecond(59)), Instant.ofEpochSecond(59), true));
        store.reset(owner, newSecret, now.plusSeconds(300));
        assertTrue(store.verify(owner, code(oldSecret, now), now, false));
        assertFalse(store.verify(owner, wrongCode(newSecret, now.plusSeconds(30)), now.plusSeconds(30), true));
        assertTrue(store.verify(owner, code(oldSecret, now.plusSeconds(30)), now.plusSeconds(30), false));
        assertTrue(store.verify(owner, code(newSecret, now.plusSeconds(60)), now.plusSeconds(60), true));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT active, failures, pending_secret_ciphertext FROM mfa_totp WHERE user_id='" + owner + "'")) {
            assertTrue(rows.next());
            assertTrue(rows.getBoolean(1));
            assertEquals(0, rows.getInt(2));
            assertEquals(null, rows.getString(3));
        }
        UUID expired = UUID.randomUUID();
        store.enroll(expired, oldSecret, now.plusSeconds(300));
        assertTrue(store.verify(expired, code(oldSecret, Instant.ofEpochSecond(59)), Instant.ofEpochSecond(59), true));
        store.reset(expired, newSecret, now.plusSeconds(5));
        assertFalse(store.verify(expired, code(newSecret, now.plusSeconds(30)), now.plusSeconds(30), true));
        assertTrue(store.verify(expired, code(oldSecret, now.plusSeconds(30)), now.plusSeconds(30), false));

        UUID sameStep = UUID.randomUUID();
        store.enroll(sameStep, oldSecret, now.plusSeconds(300));
        assertTrue(store.verify(sameStep, code(oldSecret, now), now, true));
        store.reset(sameStep, newSecret, now.plusSeconds(300));
        assertTrue(store.verify(sameStep, code(newSecret, now), now, true));
    }

    private static String code(byte[] secret, Instant now) throws Exception {
        var mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(new javax.crypto.spec.SecretKeySpec(secret, "HmacSHA1"));
        byte[] digest = mac.doFinal(java.nio.ByteBuffer.allocate(8).putLong(now.getEpochSecond() / 30).array());
        int offset = digest[19] & 15;
        int value = java.nio.ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
        return String.format(java.util.Locale.ROOT, "%06d", value % 1_000_000);
    }

    private static String wrongCode(byte[] secret, Instant now) throws Exception {
        var accepted = java.util.Set.of(code(secret, now.minusSeconds(30)), code(secret, now), code(secret, now.plusSeconds(30)));
        String candidate = "000000";
        while (accepted.contains(candidate)) candidate = String.format(java.util.Locale.ROOT, "%06d", (Integer.parseInt(candidate) + 1) % 1_000_000);
        return candidate;
    }

    @Test
    void confirmationAndConsumptionSurviveStoreRecreationAndConcurrentRequests() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.execute(new String(getClass().getResourceAsStream("/db/mfa-schema.sql").readAllBytes(), StandardCharsets.UTF_8));
        }
        SecretCipher cipher = new SecretCipher(new byte[32]);
        JdbcTotpStore first = new JdbcTotpStore(dataSource, cipher);
        UUID owner = UUID.randomUUID();
        first.enroll(owner, "12345678901234567890".getBytes(StandardCharsets.US_ASCII), Instant.ofEpochSecond(300));
        assertFalse(first.verify(owner, "287082", Instant.ofEpochSecond(59), false));
        JdbcTotpStore second = new JdbcTotpStore(dataSource, cipher);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> first.verify(owner, "287082", Instant.ofEpochSecond(59), true));
            var b = executor.submit(() -> second.verify(owner, "287082", Instant.ofEpochSecond(59), true));
            assertNotEquals(a.get(), b.get());
        }
        assertFalse(second.verify(owner, "287082", Instant.ofEpochSecond(59), false));
        Instant stepUpTime = Instant.ofEpochSecond(1_111_111_109);
        UUID challenge = first.challenge(owner, stepUpTime);
        assertFalse(second.complete(UUID.randomUUID(), challenge, "081804", stepUpTime));
        assertTrue(second.complete(owner, challenge, "081804", stepUpTime));
        assertFalse(first.complete(owner, challenge, "050471", Instant.ofEpochSecond(1_111_111_111)));
        Instant nextStep = stepUpTime.plusSeconds(30);
        UUID failedChallenge = first.challenge(owner, nextStep);
        assertFalse(first.complete(owner, failedChallenge, wrongCode("12345678901234567890".getBytes(StandardCharsets.US_ASCII), nextStep), nextStep));
        assertThrows(IllegalArgumentException.class, () -> first.challenge(owner, nextStep));
        assertFalse(first.complete(owner, failedChallenge, code("12345678901234567890".getBytes(StandardCharsets.US_ASCII), nextStep), nextStep));
        assertFalse(second.verify(UUID.randomUUID(), "081804", Instant.ofEpochSecond(1_111_111_109), false));
        for (int i = 0; i < 5; i++) {
            assertFalse(first.verify(owner, "invalid", Instant.ofEpochSecond(1_111_111_111), false));
        }
        assertFalse(second.verify(owner, "050471", Instant.ofEpochSecond(1_111_111_111), false));
        UUID expiredOwner = UUID.randomUUID();
        first.enroll(expiredOwner, "12345678901234567890".getBytes(StandardCharsets.US_ASCII), Instant.ofEpochSecond(59));
        assertFalse(second.verify(expiredOwner, "287082", Instant.ofEpochSecond(59), true));
    }
}
