package com.example.discord.auth;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void signsUpAndReturnsAccessTokenWithProfile() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
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
            .andExpect(jsonPath("$.accessToken", not(blankOrNullString())))
            .andExpect(jsonPath("$.user.username").value("vibe_signup"))
            .andExpect(jsonPath("$.user.displayName").value("Vibe Signup"));
    }

    @Test
    void logsInAndUsesBearerTokenForMeProfile() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
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
                .header("X-Forwarded-For", "198.51.100.10")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "login@example.com",
                      "password": "correct horse battery staple"
                    }
                    """))
            .andExpect(status().isOk())
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
    void locksLoginAfterRepeatedInvalidPasswordAttempts() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
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
                    .header("X-Forwarded-For", "198.51.100." + (20 + i))
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
                .header("X-Forwarded-For", "198.51.100.22")
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
        mockMvc.perform(post("/api/auth/signup")
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

        mockMvc.perform(post("/api/auth/signup")
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
                .header("X-Forwarded-For", "198.51.100.30")
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
        mockMvc.perform(post("/api/auth/signup")
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
                .header("X-Forwarded-For", "198.51.100.40")
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
    void locksUnknownEmailAfterRepeatedInvalidLoginAttempts() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .header("X-Forwarded-For", "198.51.100." + (50 + i))
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
                .header("X-Forwarded-For", "198.51.100.52")
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
        mockMvc.perform(post("/api/auth/signup")
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
}
