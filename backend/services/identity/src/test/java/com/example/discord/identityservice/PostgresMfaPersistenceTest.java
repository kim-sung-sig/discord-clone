package com.example.discord.identityservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.discord.identity.AccessTokenService;
import java.math.BigInteger;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in real PostgreSQL verification for the identity MFA slice. Runs only with
 * DISCORD_RUN_POSTGRES_TESTS=true against a disposable Testcontainers database owned by this test.
 * Evidence lines are prefixed with [pg-evidence] and never contain secrets, tokens, or passwords.
 */
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PostgresMfaPersistenceTest {
    private static final String IMAGE = "postgres:16-alpine";
    /** Fixed demo origin; WebAuthnConfiguration validates its shape, not the bound server port. */
    private static final String ORIGIN = "http://localhost:8080";
    private static final String RFC_SECRET = "12345678901234567890";
    private static final AtomicReference<Instant> TIME = new AtomicReference<>(Instant.parse("2026-09-19T00:00:00Z"));
    private static final Clock CLOCK = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return TIME.get(); }
    };
    private static final String ENCRYPTION_KEY = randomKey();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(IMAGE)
        .withDatabaseName("mfa_pg_verify")
        .withUsername("mfa_pg_verify")
        .withPassword(UUID.randomUUID().toString())
        .withLabel("discord.task", "mfa-postgres-verification");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private final JsonMapper json = JsonMapper.builder().build();

    @BeforeAll
    static void prepareTaskOwnedDatabase() {
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(dataSource);
        // Re-applying the schema must be non-destructive on PostgreSQL as well.
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(dataSource);
        evidence("image=" + IMAGE + " container=" + POSTGRES.getContainerId().substring(0, 12)
            + " database=" + POSTGRES.getDatabaseName() + " user=" + POSTGRES.getUsername());
        evidence("server=" + jdbc.queryForObject("SELECT version()", String.class));
        evidence("tables=" + jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() ORDER BY table_name", String.class));
    }

    @Test
    @Order(1)
    void totpStoreKeepsLockedStateAcrossAdapterRecreationAndConcurrency() throws Exception {
        var cipher = new SecretCipher(Base64.getDecoder().decode(ENCRYPTION_KEY));
        var first = new JdbcTotpStore(dataSource, cipher);
        UUID owner = UUID.randomUUID();
        byte[] secret = RFC_SECRET.getBytes(StandardCharsets.US_ASCII);
        assertTrue(first.enroll(owner, secret, Instant.ofEpochSecond(300)));
        assertFalse(first.enroll(owner, secret, Instant.ofEpochSecond(300)), "duplicate enrollment must map SQLState 23505 to false");
        assertFalse(first.verify(owner, "287082", Instant.ofEpochSecond(59), false), "inactive enrollment cannot authenticate");

        var second = new JdbcTotpStore(dataSource, cipher); // adapter recreation over the same database
        List<Boolean> confirmations = concurrently(8, index -> (index % 2 == 0 ? first : second)
            .verify(owner, "287082", Instant.ofEpochSecond(59), true));
        assertEquals(1, confirmations.stream().filter(Boolean::booleanValue).count(), "exactly one concurrent confirmation wins");
        evidence("t1.concurrentConfirm results=" + confirmations);

        Instant stepUp = Instant.ofEpochSecond(1_111_111_109);
        List<UUID> challenges = concurrently(8, index -> {
            try {
                return second.challenge(owner, stepUp);
            } catch (IllegalArgumentException throttled) {
                return null;
            }
        });
        List<UUID> issued = challenges.stream().filter(Objects::nonNull).toList();
        assertEquals(1, issued.size(), "conditional UPDATE issues exactly one challenge per 30s window");
        UUID challenge = issued.getFirst();
        evidence("t1.concurrentChallenge issued=" + issued.size() + " of " + challenges.size());

        List<Boolean> completions = concurrently(8, index -> (index % 2 == 0 ? first : second)
            .complete(owner, challenge, "081804", stepUp));
        assertEquals(1, completions.stream().filter(Boolean::booleanValue).count(), "row lock allows exactly one completion");
        evidence("t1.concurrentComplete results=" + completions);
        Map<String, Object> afterCompletion = jdbc.queryForMap("SELECT failures, challenge_id FROM mfa_totp WHERE user_id = ?", owner.toString());
        assertEquals(0, ((Number) afterCompletion.get("failures")).intValue(), "losers of the race do not count as failures");
        assertNull(afterCompletion.get("challenge_id"), "winner consumed the challenge");
        assertFalse(first.complete(owner, challenge, "081804", stepUp), "consumed challenge cannot be replayed");
        assertFalse(second.complete(UUID.randomUUID(), challenge, "081804", stepUp), "another owner cannot use the challenge");
        assertFalse(second.verify(owner, "081804", stepUp, false), "consumed TOTP step cannot be reused");
        // The step-reuse rejection above counts as one failure; four more one-shot challenges lock the account.
        assertEquals(1, jdbc.queryForObject("SELECT failures FROM mfa_totp WHERE user_id = ?", Integer.class, owner.toString()));

        Instant later = Instant.ofEpochSecond(1_234_567_890);
        UUID retry = new JdbcTotpStore(dataSource, cipher).challenge(owner, later);
        Instant attemptTime = later;
        for (int attempt = 0; attempt < 4; attempt++) {
            if (attempt > 0) {
                attemptTime = later.plusSeconds(30L * attempt);
                retry = new JdbcTotpStore(dataSource, cipher).challenge(owner, attemptTime);
            }
            assertFalse(first.complete(owner, retry, "invalid", attemptTime));
        }
        assertFalse(second.complete(owner, retry, "005924", attemptTime), "fail-closed after five failures even with the right code");
        assertThrows(IllegalArgumentException.class, () -> first.challenge(owner, later.plusSeconds(120)));
        Map<String, Object> row = jdbc.queryForMap("SELECT active, failures, consumed_step FROM mfa_totp WHERE user_id = ?", owner.toString());
        assertEquals(Boolean.TRUE, row.get("active"));
        assertEquals(5, ((Number) row.get("failures")).intValue());
        assertEquals(1_111_111_109L / 30, ((Number) row.get("consumed_step")).longValue());
        evidence("t1.persistedRow active=" + row.get("active") + " failures=" + row.get("failures") + " consumed_step=" + row.get("consumed_step"));
    }

    @Test
    @Order(2)
    void runtimeRestartKeepsTotpSecretPasskeyCredentialAndSingleUseChallenges() throws Exception {
        UUID owner = UUID.randomUUID();
        var device = new PasskeyTestDevice();
        var browser = HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();

        String firstOrigin = ORIGIN;
        ConfigurableApplicationContext firstRun = boot();
        String firstBase = baseUrl(firstRun);
        String secret;
        try {
            AccessTokenService tokens = firstRun.getBean(AccessTokenService.class);
            String access = "Bearer " + tokens.issue(owner);
            HttpResponse<String> enrolled = post(browser, firstBase, "/api/auth/mfa/totp/enroll", access, null, null);
            assertEquals(200, enrolled.statusCode());
            secret = json.readTree(enrolled.body()).path("secret").asText();
            assertEquals(409, post(browser, firstBase, "/api/auth/mfa/totp/enroll", access, null, null).statusCode(),
                "PostgreSQL unique violation maps to 409 without replacing the secret");
            assertEquals(204, post(browser, firstBase, "/api/auth/mfa/totp/confirm", access, null, code(secret)).statusCode());
            String challenge = json.readTree(post(browser, firstBase, "/api/auth/mfa/totp/challenges", access, null, null).body())
                .path("challengeId").asText();
            advance(30);
            HttpResponse<String> completed = post(browser, firstBase, "/api/auth/mfa/totp/challenges/" + challenge + "/complete", access, null, code(secret));
            assertEquals(200, completed.statusCode());
            String totpToken = json.readTree(completed.body()).path("accessToken").asText();
            assertEquals(List.of("totp"), tokens.verify(totpToken).amr());
            evidence("t2.run1 totp enroll/confirm/challenge/complete ok");

            String mfaBearer = "Bearer " + totpToken;
            HttpResponse<String> registrationOptions = post(browser, firstBase, "/api/auth/mfa/webauthn/register/options", mfaBearer, firstOrigin, null);
            assertEquals(200, registrationOptions.statusCode());
            JsonNode creation = json.readTree(registrationOptions.body());
            assertEquals("required", creation.at("/authenticatorSelection/userVerification").asText());
            assertEquals(204, post(browser, firstBase, "/api/auth/mfa/webauthn/register/complete", mfaBearer, firstOrigin,
                device.registration(creation, firstOrigin)).statusCode());
            assertEquals(1, credentialCount(owner));
            evidence("t2.run1 native passkey registration persisted user_credentials=" + credentialCount(owner));
        } finally {
            firstRun.close();
        }
        assertThrows(java.io.IOException.class, () -> post(browser, firstBase, "/actuator/health", null, null, null),
            "first runtime must be down before the restart");
        evidence("t2 run1 closed; embedded server refused connections");

        advance(30);
        String secondOrigin = ORIGIN;
        ConfigurableApplicationContext secondRun = boot();
        String secondBase = baseUrl(secondRun);
        evidence("t2 run2 booted on a fresh embedded server against the same database");
        try {
            AccessTokenService tokens = secondRun.getBean(AccessTokenService.class);
            String access = "Bearer " + tokens.issue(owner);
            HttpResponse<String> challenged = post(browser, secondBase, "/api/auth/mfa/totp/challenges", access, null, null);
            assertEquals(200, challenged.statusCode(), "active enrollment survives the restart");
            String challenge = json.readTree(challenged.body()).path("challengeId").asText();
            advance(30);
            HttpResponse<String> completed = post(browser, secondBase, "/api/auth/mfa/totp/challenges/" + challenge + "/complete", access, null, code(secret));
            assertEquals(200, completed.statusCode(), "secret ciphertext decrypts with the same key after restart");
            assertEquals(List.of("totp"), tokens.verify(json.readTree(completed.body()).path("accessToken").asText()).amr());
            assertEquals(401, post(browser, secondBase, "/api/auth/mfa/totp/challenges/" + challenge + "/complete", access, null, code(secret)).statusCode(),
                "TOTP challenge is single use");
            evidence("t2.run2 totp challenge/complete ok, replay=401");

            HttpResponse<String> assertionOptions = post(browser, secondBase, "/api/auth/mfa/webauthn/authenticate/options", access, secondOrigin, null);
            assertEquals(200, assertionOptions.statusCode(), "persisted credential is offered after restart");
            JsonNode request = json.readTree(assertionOptions.body());
            assertEquals(1, request.path("allowCredentials").size());
            String assertion = device.assertion(request, secondOrigin);
            HttpResponse<String> authenticated = post(browser, secondBase, "/api/auth/mfa/webauthn/authenticate/complete", access, secondOrigin, assertion);
            assertEquals(200, authenticated.statusCode(), "native assertion verifies against the persisted public key");
            var claims = tokens.verify(json.readTree(authenticated.body()).path("accessToken").asText());
            assertEquals(owner, claims.userId());
            assertEquals(List.of("passkey"), claims.amr());
            assertEquals(401, post(browser, secondBase, "/api/auth/mfa/webauthn/authenticate/complete", access, secondOrigin, assertion).statusCode(),
                "WebAuthn challenge is single use");
            evidence("t2.run2 passkey authenticate ok amr=" + claims.amr() + ", replay=401");

            advance(11);
            JsonNode again = json.readTree(post(browser, secondBase, "/api/auth/mfa/webauthn/authenticate/options", access, secondOrigin, null).body());
            String racing = device.assertion(again, secondOrigin);
            List<Integer> statuses = concurrently(4, index ->
                post(browser, secondBase, "/api/auth/mfa/webauthn/authenticate/complete", access, secondOrigin, racing).statusCode());
            assertEquals(List.of(200, 401, 401, 401), statuses.stream().sorted().toList(), "concurrent completions consume the pending challenge once");
            evidence("t2.run2 concurrent authenticate statuses=" + statuses);

            Map<String, Object> credential = jdbc.queryForMap(
                "SELECT signature_count, last_used, uv_initialized FROM user_credentials c JOIN user_entities u ON u.id = c.user_entity_user_id WHERE u.name = ?",
                owner.toString());
            assertEquals((long) device.signCount(), ((Number) credential.get("signature_count")).longValue());
            assertNotNull(credential.get("last_used"));
            Map<String, Object> totp = jdbc.queryForMap("SELECT active, failures, challenge_id FROM mfa_totp WHERE user_id = ?", owner.toString());
            assertEquals(Boolean.TRUE, totp.get("active"));
            assertEquals(0, ((Number) totp.get("failures")).intValue());
            assertNull(totp.get("challenge_id"));
            evidence("t2.persisted signature_count=" + credential.get("signature_count") + " last_used_set=" + (credential.get("last_used") != null)
                + " totp.active=" + totp.get("active") + " totp.failures=" + totp.get("failures"));
        } finally {
            secondRun.close();
        }
    }

    /** Boots the real identity runtime on a random loopback port with the fixed demo origin. */
    private static ConfigurableApplicationContext boot() {
        Map<String, Object> properties = new HashMap<>();
        properties.put("spring.main.banner-mode", "off");
        properties.put("logging.level.root", "WARN");
        properties.put("server.address", "127.0.0.1");
        properties.put("server.port", 0);
        properties.put("discord.auth.jwt.issuer", "discord-identity");
        properties.put("discord.auth.jwt.audience", "discord-api");
        properties.put("discord.auth.jwt.key-id", "identity-2026-07");
        properties.put("discord.auth.jwt.private-key-location", keyFixture("ed25519-private.pem"));
        properties.put("discord.auth.jwt.public-key-locations.identity-2026-07", keyFixture("ed25519-public.pem"));
        properties.put("discord.auth.mfa.enabled", "true");
        properties.put("discord.auth.mfa.jdbc-url", POSTGRES.getJdbcUrl());
        properties.put("discord.auth.mfa.database-user", POSTGRES.getUsername());
        properties.put("discord.auth.mfa.database-password", POSTGRES.getPassword());
        properties.put("discord.auth.mfa.encryption-key", ENCRYPTION_KEY);
        properties.put("discord.auth.webauthn.enabled", "true");
        properties.put("discord.auth.webauthn.origin", ORIGIN);
        return new SpringApplicationBuilder(IdentityServiceApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties(properties)
            .initializers(new ApplicationContextInitializer<GenericApplicationContext>() {
                @Override
                public void initialize(GenericApplicationContext context) {
                    context.registerBean("verificationClock", Clock.class, () -> CLOCK, definition -> definition.setPrimary(true));
                }
            })
            .run();
    }

    private static String keyFixture(String name) {
        return "file:" + Path.of("..", "..", "modules", "identity", "src", "test", "resources", "identity", name).toAbsolutePath();
    }

    private static HttpResponse<String> post(HttpClient client, String base, String path, String bearer, String origin, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(base + path))
            .POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) {
            request.header("Content-Type", "application/json");
        }
        if (bearer != null) {
            request.header("Authorization", bearer);
        }
        if (origin != null) {
            request.header("Origin", origin);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static int credentialCount(UUID owner) {
        Integer count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM user_credentials c JOIN user_entities u ON u.id = c.user_entity_user_id WHERE u.name = ?", Integer.class, owner.toString());
        return count == null ? 0 : count;
    }

    private static String baseUrl(ConfigurableApplicationContext context) {
        String port = context.getEnvironment().getRequiredProperty("local.server.port");
        return "http://127.0.0.1:" + port;
    }

    private static void advance(int seconds) {
        TIME.updateAndGet(now -> now.plusSeconds(seconds));
    }

    /** One indexed unit of concurrent work; checked exceptions surface through Future.get. */
    @FunctionalInterface
    private interface IndexedTask<T> {
        T run(int index) throws Exception;
    }

    private static <T> List<T> concurrently(int count, IndexedTask<T> task) throws Exception {
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(count)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int current = index;
                Callable<T> callable = () -> {
                    start.await();
                    return task.run(current);
                };
                futures.add(executor.submit(callable));
            }
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get());
            }
            return results;
        }
    }

    /** Independent RFC 6238 authenticator computation for the Base32 secret the server returned. */
    private static String code(String base32) throws Exception {
        BigInteger value = BigInteger.ZERO;
        for (char character : base32.toCharArray()) {
            value = value.shiftLeft(5).or(BigInteger.valueOf("ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".indexOf(character)));
        }
        byte[] encoded = value.toByteArray();
        byte[] secret = new byte[20];
        int count = Math.min(encoded.length, secret.length);
        System.arraycopy(encoded, encoded.length - count, secret, secret.length - count, count);
        var mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret, "HmacSHA1"));
        byte[] digest = mac.doFinal(ByteBuffer.allocate(8).putLong(TIME.get().getEpochSecond() / 30).array());
        int offset = digest[19] & 15;
        int binary = ByteBuffer.wrap(digest, offset, 4).getInt() & 0x7fffffff;
        return "{\"code\":\"" + String.format(Locale.ROOT, "%06d", binary % 1_000_000) + "\"}";
    }

    private static String randomKey() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    private static void evidence(String line) {
        System.out.println("[pg-evidence] " + line);
    }
}
