package com.example.discord.identityservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.discord.identity.AccessTokenService;
import com.example.discord.identity.PemLocationReader;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Contract of the local MFA page: the static assets the browser loads, the loopback-only CSP, the absence of storage
 * APIs, recovery controls and the token boundary between the boot legacy-auth issuer (same key contract) and identity MFA.
 */
@SpringBootTest(properties = {
    "discord.auth.mfa.enabled=true", "discord.auth.webauthn.enabled=true", "server.address=127.0.0.1",
    "discord.auth.webauthn.origin=http://localhost:8080",
    "discord.auth.mfa.jdbc-url=jdbc:h2:mem:local-ui-runtime;DB_CLOSE_DELAY=-1",
    "discord.auth.mfa.database-user=sa", "discord.auth.mfa.database-password=",
    "discord.auth.mfa.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
class MfaLocalUiRuntimeTest extends IdentityServiceApplicationTest {
    private static final String ORIGIN = "http://localhost:8080";
    private static final String EXPECTED_CSP = "default-src 'self'; script-src 'self'; "
        + "connect-src 'self' http://127.0.0.1:* http://localhost:*; object-src 'none'; base-uri 'none'; form-action 'none'";
    private static final Pattern INLINE_SCRIPT = Pattern.compile("<script(?![^>]*\\bsrc=)[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTERNAL_SCRIPT = Pattern.compile("<script[^>]*\\bsrc=\"([^\"]*)\"");

    @Autowired private MockMvc mvc;
    @Autowired private AccessTokenService identityTokens;
    @Autowired private DataSource mfaDataSource;

    @Test
    void pageLoadsOnlySameOriginModulesUnderLoopbackOnlyCspAndUsesNoBrowserStorage() throws Exception {
        String html = body(mvc.perform(get("/mfa-webauthn.html")).andExpect(status().isOk()).andReturn());
        assertThat(html).contains("content=\"" + EXPECTED_CSP + "\"");
        assertThat(INLINE_SCRIPT.matcher(html).find()).as("no inline script").isFalse();
        var scripts = EXTERNAL_SCRIPT.matcher(html);
        assertThat(scripts.find()).isTrue();
        assertThat(scripts.group(1)).isEqualTo("/mfa-webauthn.js");
        assertThat(scripts.find()).as("exactly one script tag").isFalse();
        assertThat(html).contains("type=\"module\"").contains("type=\"password\"").contains("id=\"login-base\"")
            .contains("id=\"credential-list\"").contains("data-action=\"credentials\"")
            .contains("data-action=\"unlock\"").contains("data-action=\"reset\"")
            .doesNotContain("id=\"token\"").doesNotContain("innerHTML");
        for (String asset : new String[] {"/mfa-webauthn.js", "/mfa-local-core.js"}) {
            MvcResult result = mvc.perform(get(asset)).andExpect(status().isOk()).andReturn();
            assertThat(result.getResponse().getContentType()).contains("javascript");
            String script = body(result);
            assertThat(script).doesNotContain("localStorage").doesNotContain("sessionStorage").doesNotContain("indexedDB")
                .doesNotContain("document.cookie").doesNotContain("console.log").doesNotContain("eval(");
        }
        String wiring = body(mvc.perform(get("/mfa-webauthn.js")).andReturn());
        assertThat(wiring).contains("import {").contains("./mfa-local-core.js").contains("cache: \"no-store\"")
            .contains("credentials: sameOrigin ? \"same-origin\" : \"omit\"")
            .contains("/api/auth/mfa/totp/unlock").contains("/api/auth/mfa/totp/reset")
            .contains("send(requireLoginBase() + path, { ...options, sameOrigin: false })")
            .doesNotContain("/api/auth/refresh").doesNotContain("demoCode");
    }

    @Test
    void loginServerTokenWithSameKeyContractReachesTotpAndProfileButNotFreshMfaOnlyEndpoints() throws Exception {
        new ResourceDatabasePopulator(new ClassPathResource("db/mfa-schema.sql")).execute(mfaDataSource);
        UUID user = UUID.randomUUID();
        String bearer = "Bearer " + loginServerIssuer().issue(user);
        assertThat(body(mvc.perform(get("/api/users/@me").header("Authorization", bearer)).andExpect(status().isOk()).andReturn()))
            .contains(user.toString());
        assertThat(body(mvc.perform(post("/api/auth/mfa/totp/enroll").header("Authorization", bearer)).andExpect(status().isOk()).andReturn()))
            .contains("\"secret\"");
        mvc.perform(post("/api/auth/mfa/webauthn/register/options").header("Origin", ORIGIN).header("Authorization", bearer))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/mfa/webauthn/authenticate/options").header("Origin", ORIGIN).header("Authorization", bearer))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/auth/mfa/webauthn/authenticate/options").header("Origin", "http://127.0.0.1:8080").header("Authorization", bearer))
            .andExpect(status().isForbidden());
        // Passkey management: the login token may list (same-origin GET carries no Origin) but never delete.
        assertThat(body(mvc.perform(get("/api/auth/mfa/webauthn/credentials").header("Authorization", bearer))
            .andExpect(status().isOk()).andReturn())).isEqualTo("[]");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/auth/mfa/webauthn/credentials/AAAA")
            .header("Origin", ORIGIN).header("Authorization", bearer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/mfa/webauthn/credentials")).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByAnotherKeyUnderTheSameKidIsRejectedEverywhere() throws Exception {
        var foreign = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var impostor = new AccessTokenService(foreign.getPrivate(), Map.of("identity-2026-07", foreign.getPublic()),
            "identity-2026-07", "discord-identity", "discord-api", Duration.ofHours(1), Clock.systemUTC());
        String bearer = "Bearer " + impostor.issue(UUID.randomUUID());
        mvc.perform(get("/api/users/@me").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/totp/enroll").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/totp/challenges").header("Authorization", bearer)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/webauthn/register/options").header("Origin", ORIGIN).header("Authorization", bearer))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/webauthn/register/options").header("Origin", ORIGIN)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/mfa/totp/enroll")).andExpect(status().isUnauthorized());
    }

    /** Mirrors boot AuthConfiguration.legacyAccessTokenService: same fixture key, issuer, audience, kid, one-hour ttl. */
    private AccessTokenService loginServerIssuer() throws Exception {
        String location = "file:" + Path.of("..", "..", "modules", "identity", "src", "test", "resources", "identity", "ed25519-private.pem").toAbsolutePath();
        byte[] encoded = Base64.getMimeDecoder().decode(PemLocationReader.readPrivateKey(location).replaceAll("-----[^-]+-----|\\s", ""));
        PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        var issuer = new AccessTokenService(key, "identity-2026-07", "discord-identity", "discord-api", Duration.ofHours(1), Clock.systemUTC());
        assertThat(identityTokens.verify(issuer.issue(UUID.randomUUID())).amr()).isEmpty();
        return issuer;
    }

    private static String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
