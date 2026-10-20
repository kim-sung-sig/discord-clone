package com.example.discord.identityservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.discord.identity.AccessTokenService;
import com.webauthn4j.converter.util.ObjectConverter;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class WebAuthnControllerTest {
    private static final String ORIGIN = "http://localhost:8080";
    private static final String ROOT = "/api/auth/mfa/webauthn/";
    private final JsonMapper json = JsonMapper.builder().build();
    private final AtomicReference<Instant> time = new AtomicReference<>(Instant.parse("2026-09-14T00:00:00Z"));
    private final Clock clock = new Clock() {
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return time.get(); }
    };
    private final UUID owner = UUID.randomUUID();
    private JdbcDataSource dataSource;
    private AccessTokenService tokens;
    private MockMvc mvc;
    private String bearer;
    private MockHttpSession session;

    @BeforeEach
    void setup() throws Exception {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(dataSource);
        activate(owner);
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        tokens = new AccessTokenService(keys.getPrivate(), Map.of("test", keys.getPublic()), "test", "issuer", "api", Duration.ofHours(1), clock);
        bearer = "Bearer " + tokens.issueMfa(owner, "totp");
        session = new MockHttpSession();
        mvc = controller();
    }

    private MockMvc controller() {
        var config = new WebAuthnConfiguration();
        var users = config.webAuthnUsers(dataSource);
        var credentials = config.webAuthnCredentials(dataSource);
        var environment = new MockEnvironment().withProperty("server.address", "127.0.0.1").withProperty("discord.auth.webauthn.origin", ORIGIN);
        var operations = config.webAuthnOperations(environment, users, credentials);
        return MockMvcBuilders.standaloneSetup(new WebAuthnController(tokens, clock, operations, users, credentials, dataSource, ORIGIN)).build();
    }

    private void activate(UUID user) {
        var store = new JdbcTotpStore(dataSource, new SecretCipher(new byte[32]));
        store.enroll(user, "12345678901234567890".getBytes(StandardCharsets.US_ASCII), Instant.ofEpochSecond(300));
        org.junit.jupiter.api.Assertions.assertTrue(store.verify(user, "287082", Instant.ofEpochSecond(59), true));
    }

    @Test
    void nativeRegistrationAndSignedAssertionPersistAndIssueShortMfaJwt() throws Exception {
        var device = new Authenticator();
        JsonNode registration = options(true);
        assertEquals("required", registration.at("/authenticatorSelection/userVerification").asText());
        assertEquals("required", registration.at("/authenticatorSelection/residentKey").asText());
        assertEquals("localhost", registration.at("/rp/id").asText());
        register(device, registration);
        mvc = controller();
        advance();
        bearer = "Bearer " + tokens.issue(owner);
        JsonNode assertion = options(false);
        assertEquals("required", assertion.path("userVerification").asText());
        assertEquals(1, assertion.path("allowCredentials").size());
        String payload = device.assertion(assertion, true, ORIGIN);
        String response = mvc.perform(request("authenticate/complete").content(payload)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var claims = tokens.verify(json.readTree(response).path("accessToken").asText());
        assertEquals(owner, claims.userId());
        assertEquals(List.of("passkey"), claims.amr());
        assertEquals(time.get(), claims.authTime());
        assertEquals(time.get().plusSeconds(300), claims.expiresAt());
        mvc.perform(request("authenticate/complete").content(payload)).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresBearerFreshMfaExactOriginAndLocalPeer() throws Exception {
        mvc.perform(post(ROOT + "register/options").header("Origin", ORIGIN)).andExpect(status().isUnauthorized());
        bearer = "Bearer malformed";
        mvc.perform(request("register/options")).andExpect(status().isUnauthorized());
        bearer = "Bearer " + tokens.issue(owner);
        mvc.perform(request("register/options")).andExpect(status().isForbidden());
        bearer = "Bearer " + tokens.issueMfa(owner, "totp");
        mvc.perform(post(ROOT + "register/options").header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(post(ROOT + "register/options").header("Authorization", bearer).header("Origin", "http://evil.localhost:8080")).andExpect(status().isForbidden());
        mvc.perform(request("register/options").with(request -> { request.setRemoteAddr("192.0.2.1"); return request; })).andExpect(status().isForbidden());
        time.updateAndGet(now -> now.plusSeconds(300));
        mvc.perform(request("register/options")).andExpect(status().isUnauthorized());
    }

    @Test
    void challengeIsExpiringSessionAndOwnerBoundAndBadPayloadConsumesIt() throws Exception {
        String payload = new Authenticator().registration(options(true), true, ORIGIN);
        var original = session;
        session = new MockHttpSession();
        mvc.perform(request("register/complete").content(payload)).andExpect(status().isUnauthorized());
        session = original;
        bearer = "Bearer " + tokens.issueMfa(UUID.randomUUID(), "totp");
        mvc.perform(request("register/complete").content(payload)).andExpect(status().isUnauthorized());
        bearer = "Bearer " + tokens.issueMfa(owner, "totp");
        time.updateAndGet(now -> now.plusSeconds(180));
        mvc.perform(request("register/complete").content(payload)).andExpect(status().isUnauthorized());
        options(true);
        mvc.perform(request("register/complete").content("null")).andExpect(status().isBadRequest());
        mvc.perform(request("register/complete").content(payload)).andExpect(status().isUnauthorized());
    }

    @Test
    void nativeVerifierRejectsMissingUvWrongOriginStaleChallengeAndBadSignature() throws Exception {
        var device = new Authenticator();
        mvc.perform(request("register/complete").content(device.registration(options(true), false, ORIGIN))).andExpect(status().isUnauthorized());
        advance();
        mvc.perform(request("register/complete").content(device.registration(options(true), true, "http://evil.test"))).andExpect(status().isUnauthorized());
        advance();
        String stale = device.registration(options(true), true, ORIGIN);
        advance();
        options(true);
        mvc.perform(request("register/complete").content(stale)).andExpect(status().isUnauthorized());
        advance();
        register(device, options(true));
        advance();
        mvc.perform(request("authenticate/complete").content(device.assertion(options(false), false, ORIGIN))).andExpect(status().isUnauthorized());
        advance();
        var tampered = json.readTree(device.assertion(options(false), true, ORIGIN));
        ((tools.jackson.databind.node.ObjectNode) tampered.path("response")).put("signature", b64(new byte[72]));
        mvc.perform(request("authenticate/complete").content(json.writeValueAsString(tampered))).andExpect(status().isUnauthorized());
    }

    @Test
    void anotherUsersCredentialCannotStepUpEvenWithValidSignature() throws Exception {
        register(new Authenticator(), options(true));
        UUID other = UUID.randomUUID();
        activate(other);
        bearer = "Bearer " + tokens.issueMfa(other, "totp");
        var otherDevice = new Authenticator();
        register(otherDevice, options(true));
        advance();
        bearer = "Bearer " + tokens.issue(owner);
        mvc.perform(request("authenticate/complete").content(otherDevice.assertion(options(false), true, ORIGIN))).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectedOptionsDoNotCreateSessions() throws Exception {
        bearer = "Bearer " + tokens.issue(UUID.randomUUID());
        var unknown = mvc.perform(post(ROOT + "authenticate/options").header("Authorization", bearer).header("Origin", ORIGIN))
            .andExpect(status().isConflict()).andReturn().getRequest();
        org.junit.jupiter.api.Assertions.assertNull(unknown.getSession(false));
        bearer = "Bearer " + tokens.issueMfa(owner, "totp");
        options(true);
        var throttled = mvc.perform(post(ROOT + "register/options").header("Authorization", bearer).header("Origin", ORIGIN))
            .andExpect(status().isTooManyRequests()).andReturn().getRequest();
        org.junit.jupiter.api.Assertions.assertNull(throttled.getSession(false));
    }

    @Test
    void accountThrottleCrossesSessionsAndConcurrentCompletionIsSingleUse() throws Exception {
        String payload = new Authenticator().registration(options(true), true, ORIGIN);
        var original = session;
        session = new MockHttpSession();
        mvc.perform(request("register/options")).andExpect(status().isTooManyRequests());
        session = original;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mvc.perform(request("register/complete").content(payload)).andReturn().getResponse().getStatus());
            var second = executor.submit(() -> mvc.perform(request("register/complete").content(payload)).andReturn().getResponse().getStatus());
            assertEquals(List.of(204, 401), java.util.stream.Stream.of(first.get(), second.get()).sorted().toList());
        }
    }

    @Test
    void capacityRemainsFiveWhenDifferentSessionsCompleteConcurrently() throws Exception {
        for (int index = 0; index < 4; index++) { register(new Authenticator(), options(true)); advance(); }
        String fifth = new Authenticator().registration(options(true), true, ORIGIN);
        var firstSession = session;
        advance();
        session = new MockHttpSession();
        String sixth = new Authenticator().registration(options(true), true, ORIGIN);
        var secondSession = session;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mvc.perform(request("register/complete").session(firstSession).content(fifth)).andReturn().getResponse().getStatus());
            var second = executor.submit(() -> mvc.perform(request("register/complete").session(secondSession).content(sixth)).andReturn().getResponse().getStatus());
            assertEquals(List.of(204, 409), java.util.stream.Stream.of(first.get(), second.get()).sorted().toList());
        }
        assertEquals(5, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM user_credentials", Integer.class));
    }

    @Test
    void listsOnlyTheCallersCredentialsAndDeletesOnlyWithFreshMfaOriginAndOwnership() throws Exception {
        register(new Authenticator(), options(true));
        advance();
        register(new Authenticator(), options(true));
        UUID other = UUID.randomUUID();
        activate(other);
        bearer = "Bearer " + tokens.issueMfa(other, "totp");
        register(new Authenticator(), options(true));
        String otherId = json.readTree(list(other).getContentAsString()).get(0).path("id").asText();

        var response = list(owner);
        assertEquals("no-store", response.getHeader("Cache-Control"));
        JsonNode mine = json.readTree(response.getContentAsString());
        assertEquals(2, mine.size());
        for (JsonNode entry : mine) {
            assertEquals(List.of("createdAt", "id", "label", "lastUsedAt"), sortedFieldNames(entry));
            assertEquals("Test device", entry.path("label").asText());
            org.junit.jupiter.api.Assertions.assertFalse(entry.path("id").asText().isBlank());
            org.junit.jupiter.api.Assertions.assertFalse(entry.path("createdAt").isNull());
        }
        String first = mine.get(0).path("id").asText();
        mvc.perform(get(ROOT + "credentials")).andExpect(status().isUnauthorized());
        mvc.perform(get(ROOT + "credentials").header("Authorization", "Bearer " + tokens.issue(owner))
            .with(request -> { request.setRemoteAddr("192.0.2.1"); return request; })).andExpect(status().isForbidden());
        assertEquals(0, json.readTree(list(UUID.randomUUID()).getContentAsString()).size());

        bearer = "Bearer " + tokens.issue(owner);
        mvc.perform(request("credentials/" + first, delete(ROOT + "credentials/" + first))).andExpect(status().isForbidden());
        bearer = "Bearer " + tokens.issueMfa(owner, "totp");
        mvc.perform(delete(ROOT + "credentials/" + first).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(request("credentials/" + first, delete(ROOT + "credentials/" + first))
            .with(request -> { request.setRemoteAddr("192.0.2.1"); return request; })).andExpect(status().isForbidden());
        mvc.perform(request("credentials/" + otherId, delete(ROOT + "credentials/" + otherId))).andExpect(status().isNotFound());
        mvc.perform(request("credentials/x", delete(ROOT + "credentials/" + b64(new byte[16])))).andExpect(status().isNotFound());
        mvc.perform(request("credentials/x", delete(ROOT + "credentials/%25%25%25"))).andExpect(status().isBadRequest());
        mvc.perform(request("credentials/x", delete(ROOT + "credentials/" + "A".repeat(1_100)))).andExpect(status().isBadRequest());
        new JdbcTemplate(dataSource).update("UPDATE mfa_totp SET failures = 5 WHERE user_id = ?", owner.toString());
        mvc.perform(request("credentials/" + first, delete(ROOT + "credentials/" + first))).andExpect(status().isNoContent());
        String last = mine.get(1).path("id").asText();
        mvc.perform(request("credentials/" + last, delete(ROOT + "credentials/" + last))).andExpect(status().isConflict());
        new JdbcTemplate(dataSource).update("UPDATE mfa_totp SET failures = 4 WHERE user_id = ?", owner.toString());
        mvc.perform(request("credentials/" + last, delete(ROOT + "credentials/" + last))).andExpect(status().isNoContent());
        mvc.perform(request("credentials/" + last, delete(ROOT + "credentials/" + last))).andExpect(status().isNotFound());
        JsonNode remaining = json.readTree(list(owner).getContentAsString());
        assertEquals(0, remaining.size());
        assertEquals(1, json.readTree(list(other).getContentAsString()).size());
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM user_credentials", Integer.class));
    }

    @Test
    void concurrentDeletionOfOneCredentialSucceedsExactlyOnce() throws Exception {
        register(new Authenticator(), options(true));
        advance();
        register(new Authenticator(), options(true));
        String id = json.readTree(list(owner).getContentAsString()).get(0).path("id").asText();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> mvc.perform(request("credentials/" + id, delete(ROOT + "credentials/" + id))).andReturn().getResponse().getStatus());
            var second = executor.submit(() -> mvc.perform(request("credentials/" + id, delete(ROOT + "credentials/" + id))).andReturn().getResponse().getStatus());
            assertEquals(List.of(204, 404), java.util.stream.Stream.of(first.get(), second.get()).sorted().toList());
        }
        assertEquals(1, json.readTree(list(owner).getContentAsString()).size());
    }

    private org.springframework.mock.web.MockHttpServletResponse list(UUID user) throws Exception {
        return mvc.perform(get(ROOT + "credentials").header("Authorization", "Bearer " + tokens.issue(user)))
            .andExpect(status().isOk()).andReturn().getResponse();
    }

    @Test
    void credentialListMapsStorageFailureToServiceUnavailable() throws Exception {
        new JdbcTemplate(dataSource).execute("SHUTDOWN");
        mvc.perform(get(ROOT + "credentials").header("Authorization", bearer))
            .andExpect(status().isServiceUnavailable());
    }

    private static List<String> sortedFieldNames(JsonNode node) {
        var names = new java.util.ArrayList<String>();
        node.propertyNames().forEach(names::add);
        return names.stream().sorted().toList();
    }

    @Test
    void boundedPayloadAndNonDestructiveSchemaUpgradeAndConfiguration() throws Exception {
        options(true);
        mvc.perform(request("register/complete").content(" ".repeat(65_537))).andExpect(status().isPayloadTooLarge());
        advance();
        var payload = (tools.jackson.databind.node.ObjectNode) json.readTree(new Authenticator().registration(options(true), true, ORIGIN));
        payload.put("label", "x".repeat(65));
        mvc.perform(request("register/complete").content(json.writeValueAsString(payload))).andExpect(status().isBadRequest());
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(dataSource);
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM mfa_totp", Integer.class));
        var config = new WebAuthnConfiguration();
        assertThrows(IllegalStateException.class, () -> config.webAuthnOperations(new MockEnvironment().withProperty("discord.auth.webauthn.origin", ORIGIN),
            config.webAuthnUsers(dataSource), config.webAuthnCredentials(dataSource)));
    }

    private void advance() { time.updateAndGet(now -> now.plusSeconds(10)); }
    private MockHttpServletRequestBuilder request(String path) {
        return request(path, post(ROOT + path));
    }
    /** Same Bearer/Origin/session/content-type contract for any HTTP method; {@code path} only documents the target. */
    private MockHttpServletRequestBuilder request(String path, MockHttpServletRequestBuilder builder) {
        return builder.session(session).header("Authorization", bearer).header("Origin", ORIGIN).contentType("application/json");
    }
    private JsonNode options(boolean registration) throws Exception {
        var response = mvc.perform(request(registration ? "register/options" : "authenticate/options")).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("no-store", response.getHeader("Cache-Control"));
        return json.readTree(response.getContentAsString());
    }
    private void register(Authenticator device, JsonNode options) throws Exception {
        mvc.perform(request("register/complete").content(device.registration(options, true, ORIGIN))).andExpect(status().isNoContent());
    }
    private static String b64(byte[] value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }

    /** Test device produces signed wire data; the server uses the real native verifier. */
    private class Authenticator {
        private final KeyPair key;
        private final byte[] id = UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII);
        private String userHandle;
        Authenticator() throws Exception {
            var generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            key = generator.generateKeyPair();
        }
        String registration(JsonNode options, boolean uv, String origin) throws Exception {
            userHandle = options.at("/user/id").asText();
            var point = ((ECPublicKey) key.getPublic()).getW();
            var cbor = new ObjectConverter().getCborConverter();
            byte[] cose = cbor.writeValueAsBytes(Map.of(1, 2, 3, -7, -1, 1, -2, coordinate(point.getAffineX()), -3, coordinate(point.getAffineY())));
            byte[] data = ByteBuffer.allocate(37 + 16 + 2 + id.length + cose.length)
                .put(hash("localhost".getBytes(StandardCharsets.UTF_8))).put((byte) (uv ? 0x45 : 0x41)).putInt(0)
                .put(new byte[16]).putShort((short) id.length).put(id).put(cose).array();
            byte[] attestation = cbor.writeValueAsBytes(Map.of("fmt", "none", "attStmt", Map.of(), "authData", data));
            var response = Map.of("clientDataJSON", b64(clientData(options, "webauthn.create", origin)), "attestationObject", b64(attestation), "transports", List.of("internal"));
            return json.writeValueAsString(Map.of("label", "Test device", "credential", credential(response)));
        }
        String assertion(JsonNode options, boolean uv, String origin) throws Exception {
            byte[] client = clientData(options, "webauthn.get", origin);
            byte[] data = ByteBuffer.allocate(37).put(hash("localhost".getBytes(StandardCharsets.UTF_8))).put((byte) (uv ? 5 : 1)).putInt(1).array();
            var signature = Signature.getInstance("SHA256withECDSA");
            signature.initSign(key.getPrivate()); signature.update(data); signature.update(hash(client));
            return json.writeValueAsString(credential(Map.of("clientDataJSON", b64(client), "authenticatorData", b64(data), "signature", b64(signature.sign()), "userHandle", userHandle)));
        }
        private Map<String, Object> credential(Object response) {
            return Map.of("id", b64(id), "rawId", b64(id), "type", "public-key", "response", response, "clientExtensionResults", Map.of());
        }
        private byte[] clientData(JsonNode options, String type, String origin) {
            return json.writeValueAsBytes(Map.of("type", type, "challenge", options.path("challenge").asText(), "origin", origin, "crossOrigin", false));
        }
        private byte[] coordinate(java.math.BigInteger value) {
            byte[] bytes = value.toByteArray();
            if (bytes.length >= 32) return Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length);
            byte[] padded = new byte[32]; System.arraycopy(bytes, 0, padded, 32 - bytes.length, bytes.length); return padded;
        }
        private byte[] hash(byte[] value) throws Exception { return MessageDigest.getInstance("SHA-256").digest(value); }
    }
}

@org.springframework.boot.test.context.SpringBootTest(properties = {
    "discord.auth.mfa.enabled=true", "discord.auth.webauthn.enabled=true", "server.address=127.0.0.1",
    "discord.auth.webauthn.origin=http://localhost:8080",
    "discord.auth.mfa.jdbc-url=jdbc:h2:mem:webauthn-runtime;DB_CLOSE_DELAY=-1",
    "discord.auth.mfa.database-user=sa", "discord.auth.mfa.database-password=",
    "discord.auth.mfa.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class WebAuthnRuntimeTest extends IdentityServiceApplicationTest {
    @org.springframework.beans.factory.annotation.Autowired private MockMvc mvc;
    @org.springframework.beans.factory.annotation.Autowired private AccessTokenService tokens;
    @org.springframework.beans.factory.annotation.Autowired private javax.sql.DataSource source;

    @Test
    void enabledRuntimeWiresNativeRepositoriesAndOptions() throws Exception {
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(source);
        UUID user = UUID.randomUUID();
        var store = new JdbcTotpStore(source, new SecretCipher(new byte[32]));
        store.enroll(user, "12345678901234567890".getBytes(StandardCharsets.US_ASCII), Instant.ofEpochSecond(300));
        org.junit.jupiter.api.Assertions.assertTrue(store.verify(user, "287082", Instant.ofEpochSecond(59), true));
        mvc.perform(post("/api/auth/mfa/webauthn/register/options").header("Origin", "http://localhost:8080")
            .header("Authorization", "Bearer " + tokens.issueMfa(user, "totp"))).andExpect(status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/mfa-webauthn.html"))
            .andExpect(status().isOk());
    }
}
