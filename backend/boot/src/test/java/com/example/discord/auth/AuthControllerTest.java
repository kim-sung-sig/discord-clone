package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class AuthControllerTest {
    private static final String REFRESH_COOKIE = "dc_refresh";
    private static final AtomicInteger SIGNUP_IP_COUNTER = new AtomicInteger(100);
    private static final AtomicInteger LOGIN_IP_COUNTER = new AtomicInteger(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthStore authStore;

    @Test
    void signsUpAndReturnsAccessTokenWithProfile() throws Exception {
        MvcResult signup = mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "signup@example.com",
                      "username": "vibe_signup",
                      "displayName": "Vibe Signup",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.user.username").value("vibe_signup"))
            .andExpect(jsonPath("$.user.displayName").value("Vibe Signup"))
            .andReturn();

        assertRefreshSetCookie(signup)
            .contains("HttpOnly", "Path=/api/auth", "Max-Age=604800", "SameSite=Lax")
            .doesNotContain("Secure");
    }

    @Test
    void forwardedHttpsSignupMarksRefreshCookieSecure() throws Exception {
        MvcResult signup = mockMvc.perform(signupRequest()
                .header("X-Forwarded-Proto", "https")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "secure-cookie@example.com",
                      "username": "vibe_secure_cookie",
                      "displayName": "Vibe Secure Cookie",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        assertRefreshSetCookie(signup)
            .contains("Secure", "SameSite=Lax", "Path=/api/auth");
    }

    @Test
    void logsInAndUsesBearerTokenForMeProfile() throws Exception {
        mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login@example.com",
                      "username": "vibe_login",
                      "displayName": "Vibe Login",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr("198.51.100.10"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login@example.com",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/users/@me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("vibe_login"))
            .andExpect(jsonPath("$.displayName").value("Vibe Login"));
    }

    @Test
    void exposesBackendOwnedGlobalAdminRoleOnMeProfile() throws Exception {
        MvcResult signup = mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "security-admin@example.com",
                      "username": "security_admin",
                      "displayName": "Security Admin",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.user.admin").value(false))
            .andExpect(jsonPath("$.user.roles", hasSize(0)))
            .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(signup.getResponse().getContentAsString(), "$.accessToken");
        String userId = com.jayway.jsonpath.JsonPath.read(signup.getResponse().getContentAsString(), "$.user.id");
        authStore.grantGlobalRole(java.util.UUID.fromString(userId), "SECURITY_ADMIN");

        mockMvc.perform(get("/api/users/@me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("security_admin"))
            .andExpect(jsonPath("$.roles[0]").value("SECURITY_ADMIN"))
            .andExpect(jsonPath("$.admin").value(true));
    }

    @Test
    void securityAdminCanReviewGlobalRoleAuditEntries() throws Exception {
        MvcResult adminSignup = signupResult("audit-review-admin@example.com", "audit_admin", "Audit Admin");
        String adminToken = com.jayway.jsonpath.JsonPath.read(adminSignup.getResponse().getContentAsString(), "$.accessToken");
        String adminId = com.jayway.jsonpath.JsonPath.read(adminSignup.getResponse().getContentAsString(), "$.user.id");
        authStore.grantGlobalRole(UUID.fromString(adminId), "SECURITY_ADMIN");

        MvcResult targetSignup = signupResult("audit-review-target@example.com", "audit_target", "Audit Target");
        UUID targetUserId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
            targetSignup.getResponse().getContentAsString(),
            "$.user.id"
        ));
        authStore.recordGlobalRoleAudit(new GlobalRoleAuditEntry(
            targetUserId,
            "SECURITY_ADMIN",
            GlobalRoleAuditAction.GRANT,
            "ops-console",
            GlobalRoleAuditResult.APPLIED,
            Instant.parse("2026-05-20T00:00:00Z")
        ));

        mockMvc.perform(get("/api/admin/global-roles/audit-log")
                .queryParam("targetUserId", targetUserId.toString())
                .queryParam("limit", "1")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries", hasSize(1)))
            .andExpect(jsonPath("$.entries[0].targetUserId").value(targetUserId.toString()))
            .andExpect(jsonPath("$.entries[0].role").value("SECURITY_ADMIN"))
            .andExpect(jsonPath("$.entries[0].action").value("GRANT"))
            .andExpect(jsonPath("$.entries[0].actor").value("ops-console"))
            .andExpect(jsonPath("$.entries[0].result").value("APPLIED"))
            .andExpect(jsonPath("$.entries[0].occurredAt").value("2026-05-20T00:00:00Z"))
            .andExpect(jsonPath("$.retention.maxAgeDays").value(365))
            .andExpect(jsonPath("$.export.formats[0]").value("json"))
            .andExpect(jsonPath("$.export.maxEntriesPerRequest").value(100))
            .andExpect(jsonPath("$.export.requiresRole").value("SECURITY_ADMIN"));
    }

    @Test
    void globalRoleAuditReviewOmitsEntriesOutsideRetentionPolicy() throws Exception {
        MvcResult adminSignup = signupResult("audit-retention-admin@example.com", "audit_retention_admin", "Audit Retention Admin");
        String adminToken = com.jayway.jsonpath.JsonPath.read(adminSignup.getResponse().getContentAsString(), "$.accessToken");
        String adminId = com.jayway.jsonpath.JsonPath.read(adminSignup.getResponse().getContentAsString(), "$.user.id");
        authStore.grantGlobalRole(UUID.fromString(adminId), "SECURITY_ADMIN");

        MvcResult targetSignup = signupResult("audit-retention-target@example.com", "audit_retention_target", "Audit Retention Target");
        UUID targetUserId = UUID.fromString(com.jayway.jsonpath.JsonPath.read(
            targetSignup.getResponse().getContentAsString(),
            "$.user.id"
        ));
        authStore.recordGlobalRoleAudit(new GlobalRoleAuditEntry(
            targetUserId,
            "SECURITY_ADMIN",
            GlobalRoleAuditAction.GRANT,
            "ops-console",
            GlobalRoleAuditResult.APPLIED,
            Instant.parse("2020-01-01T00:00:00Z")
        ));

        mockMvc.perform(get("/api/admin/global-roles/audit-log")
                .queryParam("targetUserId", targetUserId.toString())
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.entries", hasSize(0)))
            .andExpect(jsonPath("$.retention.maxAgeDays").value(365));
    }

    @Test
    void rejectsNonSecurityAdminGlobalRoleAuditReview() throws Exception {
        MvcResult signup = signupResult("audit-review-member@example.com", "audit_member", "Audit Member");
        String token = com.jayway.jsonpath.JsonPath.read(signup.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/admin/global-roles/audit-log")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("forbidden"));
    }

    @Test
    void globalRoleAuditReviewRejectsMissingBearerToken() throws Exception {
        mockMvc.perform(get("/api/admin/global-roles/audit-log"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("bearer token required"));
    }

    @Test
    void globalRoleAuditReviewRejectsLimitOutsideExportBounds() throws Exception {
        MvcResult adminSignup = signupResult("audit-limit-admin@example.com", "audit_limit_admin", "Audit Limit Admin");
        String adminToken = com.jayway.jsonpath.JsonPath.read(adminSignup.getResponse().getContentAsString(), "$.accessToken");
        String adminId = com.jayway.jsonpath.JsonPath.read(adminSignup.getResponse().getContentAsString(), "$.user.id");
        authStore.grantGlobalRole(UUID.fromString(adminId), "SECURITY_ADMIN");

        mockMvc.perform(get("/api/admin/global-roles/audit-log")
                .queryParam("limit", "0")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));

        mockMvc.perform(get("/api/admin/global-roles/audit-log")
                .queryParam("limit", "101")
                .header("Authorization", "Bearer " + adminToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));
    }

    @Test
    void locksLoginAfterRepeatedInvalidPasswordAttempts() throws Exception {
        mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "lockout@example.com",
                      "username": "vibe_lockout",
                      "displayName": "Vibe Lockout",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isCreated());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr("198.51.100." + (20 + i)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "lockout@example.com",
                          "password": "wrong password"
                        }
                        """))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr("198.51.100.22"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "lockout@example.com",
                      "password": "wrong password"
                    }
                    """))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.message").value("login temporarily locked"));
    }

    @Test
    void duplicateSignupReturnsConflictAndDoesNotOverwriteExistingAccount() throws Exception {
        mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "duplicate@example.com",
                      "username": "vibe_original",
                      "displayName": "Original User",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "duplicate@example.com",
                      "username": "vibe_overwrite",
                      "displayName": "Overwrite User",
                      "password": "different password"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("account already exists"));

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr("198.51.100.30"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "duplicate@example.com",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(get("/api/users/@me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("vibe_original"))
            .andExpect(jsonPath("$.displayName").value("Original User"));
    }

    @Test
    void logoutRevokesBearerToken() throws Exception {
        mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "logout@example.com",
                      "username": "vibe_logout",
                      "displayName": "Vibe Logout",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr("198.51.100.40"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "logout@example.com",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        String token = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");

        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/@me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesCookieAndRejectsReuseByRevokingSessionFamily() throws Exception {
        signup("refresh@example.com", "vibe_refresh", "Vibe Refresh");
        MvcResult login = login("refresh@example.com", "correct horse battery staple", "Chrome on Windows");
        Cookie firstRefresh = refreshCookie(login);

        MvcResult refresh = mockMvc.perform(post("/api/auth/refresh")
                .cookie(firstRefresh))
            .andExpect(status().isOk())
            .andExpect(cookie().httpOnly(REFRESH_COOKIE, true))
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andReturn();
        Cookie rotatedRefresh = refreshCookie(refresh);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(firstRefresh))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("refresh token reuse detected"));

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(rotatedRefresh))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void listsAndRevokesOwnRefreshSessions() throws Exception {
        signup("sessions@example.com", "vibe_sessions", "Vibe Sessions");
        MvcResult login = login("sessions@example.com", "correct horse battery staple", "Firefox on Linux");
        String accessToken = com.jayway.jsonpath.JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        Cookie refreshCookie = refreshCookie(login);

        MvcResult sessions = mockMvc.perform(get("/api/auth/sessions")
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sessions", hasSize(2)))
            .andExpect(jsonPath("$.sessions[1].deviceName").value("Firefox on Linux"))
            .andExpect(jsonPath("$.sessions[1].revoked").value(false))
            .andReturn();

        String sessionId = com.jayway.jsonpath.JsonPath.read(
            sessions.getResponse().getContentAsString(),
            "$.sessions[1].id"
        );

        mockMvc.perform(delete("/api/auth/sessions/{sessionId}", sessionId)
                .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void logsSuspiciousLoginCandidateWhenDeviceIsNew(CapturedOutput output) throws Exception {
        signup("audit@example.com", "vibe_audit", "Vibe Audit");

        login("audit@example.com", "correct horse battery staple", "New Device Browser");

        assertThat(output.getOut())
            .contains("auth suspicious login detected")
            .contains("device_name=New Device Browser");
    }

    @Test
    void locksUnknownEmailAfterRepeatedInvalidLoginAttempts() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr("198.51.100." + (50 + i)))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "unknown@example.com",
                          "password": "wrong password"
                        }
                        """))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr("198.51.100.52"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "unknown@example.com",
                      "password": "wrong password"
                    }
                    """))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.message").value("login temporarily locked"));
    }

    @Test
    void invalidSignupEmailReturnsBadRequest() throws Exception {
        mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "username": "vibe_invalid",
                      "displayName": "Invalid User",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));
    }

    @Test
    void logsOutWithoutLeakingAuthenticationState() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
            .andExpect(status().isNoContent());
    }

    @Test
    void logoutClearsRefreshCookieWithMatchingAttributes() throws Exception {
        MvcResult signup = signupResult("clear-cookie@example.com", "vibe_clear_cookie", "Vibe Clear Cookie");
        Cookie refreshCookie = refreshCookie(signup);

        MvcResult logout = mockMvc.perform(post("/api/auth/logout")
                .cookie(refreshCookie))
            .andExpect(status().isNoContent())
            .andReturn();

        assertRefreshSetCookie(logout)
            .contains("dc_refresh=", "HttpOnly", "Path=/api/auth", "Max-Age=0", "SameSite=Lax")
            .doesNotContain("Secure");
    }

    private void signup(String email, String username, String displayName) throws Exception {
        signupResult(email, username, displayName);
    }

    private MvcResult signupResult(String email, String username, String displayName) throws Exception {
        return mockMvc.perform(signupRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "username": "%s",
                      "displayName": "%s",
                      "password": "correct horse battery staple"
                    }
                    """.formatted(email, username, displayName)))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private static MockHttpServletRequestBuilder signupRequest() {
        return post("/api/auth/signup")
            .with(remoteAddr("203.0.113." + SIGNUP_IP_COUNTER.getAndIncrement()));
    }

    private MvcResult login(String email, String password, String userAgent) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(remoteAddr("198.51.100." + LOGIN_IP_COUNTER.getAndIncrement()))
                .header("User-Agent", userAgent)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn();
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(REFRESH_COOKIE);
        org.assertj.core.api.Assertions.assertThat(cookie).isNotNull();
        return cookie;
    }

    private static org.assertj.core.api.AbstractStringAssert<?> assertRefreshSetCookie(MvcResult result) {
        return assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE))
            .isNotNull()
            .startsWith(REFRESH_COOKIE + "=");
    }

    private static RequestPostProcessor remoteAddr(String remoteAddr) {
        return request -> {
            request.setRemoteAddr(remoteAddr);
            return request;
        };
    }
}
