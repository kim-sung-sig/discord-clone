package com.example.discord.presence;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PresenceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void updatesAndReadsAuthenticatedUserPresenceStatus() throws Exception {
        AuthSession user = signup("presence_status_user");

        mockMvc.perform(put("/api/presence/me")
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "ONLINE",
                      "ttlSeconds": 60
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.userId().toString()))
            .andExpect(jsonPath("$.status").value("ONLINE"));

        mockMvc.perform(get("/api/presence/users/{userId}", user.userId())
                .header("Authorization", user.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.userId").value(user.userId().toString()))
            .andExpect(jsonPath("$.status").value("ONLINE"));
    }

    @Test
    void typingEndpointReturnsAuthenticatedTypingUser() throws Exception {
        AuthSession user = signup("presence_typing_user");
        UUID channelId = UUID.fromString("00000000-0000-0000-0000-000000000101");

        mockMvc.perform(put("/api/presence/channels/{channelId}/typing", channelId)
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ttlSeconds": 5
                    }
                    """))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/presence/channels/{channelId}/typing", channelId)
                .header("Authorization", user.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.typingUserIds", contains(user.userId().toString())));
    }

    @Test
    void readMarkerAndUnreadEndpointUseAuthenticatedUser() throws Exception {
        AuthSession user = signup("presence_read_user");
        UUID channelId = UUID.fromString("00000000-0000-0000-0000-000000000102");

        mockMvc.perform(put("/api/presence/channels/{channelId}/read", channelId)
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSequence": 42
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channelId").value(channelId.toString()))
            .andExpect(jsonPath("$.userId").value(user.userId().toString()))
            .andExpect(jsonPath("$.lastReadSequence").value(42));

        mockMvc.perform(post("/api/presence/channels/{channelId}/unread-count", channelId)
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastMessageSequence": 50,
                      "authoredSequences": [45, 47, 47, 60]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unreadCount").value(6));
    }

    private AuthSession signup(String username) throws Exception {
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s@example.com",
                      "username": "%s",
                      "displayName": "%s",
                      "password": "correct horse battery staple"
                    }
                    """.formatted(username, username, username)))
            .andExpect(status().isCreated())
            .andReturn();

        String body = signup.getResponse().getContentAsString();
        return new AuthSession(
            JsonPath.read(body, "$.accessToken"),
            UUID.fromString(JsonPath.read(body, "$.user.id"))
        );
    }

    private record AuthSession(String accessToken, UUID userId) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
