package com.example.discord.ops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.discord.DiscordApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = DiscordApplication.class,
    properties = "discord.trusted-proxy.cidrs=10.0.0.0/24"
)
@AutoConfigureMockMvc
class OperationalHardeningFilterTrustedProxyTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void authLoginRateLimitUsesForwardedClientIpFromAllowlistedProxy() throws Exception {
        String proxyRemoteAddr = "10.0.0.25";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(request -> {
                        request.setRemoteAddr(proxyRemoteAddr);
                        return request;
                    })
                    .header("X-Forwarded-For", "203.0.113.210")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "trusted-proxy-a-%d@example.com",
                          "password": "wrong password"
                        }
                        """.formatted(i)))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(proxyRemoteAddr);
                    return request;
                })
                .header("X-Forwarded-For", "203.0.113.210")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "trusted-proxy-a-final@example.com",
                      "password": "wrong password"
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("X-RateLimit-Limit", "2"))
            .andExpect(jsonPath("$.message").value("rate limit exceeded"));

        mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(proxyRemoteAddr);
                    return request;
                })
                .header("X-Forwarded-For", "203.0.113.211")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "trusted-proxy-b@example.com",
                      "password": "wrong password"
                    }
                """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void authLoginRateLimitUsesRealIpFallbackFromAllowlistedProxy() throws Exception {
        String proxyRemoteAddr = "10.0.0.26";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/auth/login")
                    .with(request -> {
                        request.setRemoteAddr(proxyRemoteAddr);
                        return request;
                    })
                    .header("X-Forwarded-For", "not-an-ip")
                    .header("X-Real-IP", "203.0.113.220")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                        {
                          "email": "trusted-real-ip-a-%d@example.com",
                          "password": "wrong password"
                        }
                        """.formatted(i)))
                .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(proxyRemoteAddr);
                    return request;
                })
                .header("X-Forwarded-For", "not-an-ip")
                .header("X-Real-IP", "203.0.113.220")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "trusted-real-ip-a-final@example.com",
                      "password": "wrong password"
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().string("X-RateLimit-Limit", "2"))
            .andExpect(jsonPath("$.message").value("rate limit exceeded"));

        mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(proxyRemoteAddr);
                    return request;
                })
                .header("X-Forwarded-For", "not-an-ip")
                .header("X-Real-IP", "203.0.113.221")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "trusted-real-ip-b@example.com",
                      "password": "wrong password"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }
}
