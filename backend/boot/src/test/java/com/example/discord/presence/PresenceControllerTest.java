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
        String guildId = createGuild(user);
        String channelId = createChannel(guildId, "presence-typing", user);

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
        String guildId = createGuild(user);
        String channelId = createChannel(guildId, "presence-read", user);

        mockMvc.perform(put("/api/presence/channels/{channelId}/read", channelId)
                .header("Authorization", user.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSequence": 42
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channelId").value(channelId))
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

    @Test
    void rejectsChannelScopedPresenceOperationsForHiddenChannel() throws Exception {
        AuthSession owner = signup("presence_hidden_owner");
        AuthSession member = signup("presence_hidden_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "presence-hidden", owner);
        addMember(guildId, member.userId(), owner);
        denyEveryoneView(guildId, channelId, owner);

        mockMvc.perform(put("/api/presence/channels/{channelId}/typing", channelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "ttlSeconds": 5
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/presence/channels/{channelId}/typing", channelId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/presence/channels/{channelId}/read", channelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastReadSequence": 42
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/presence/channels/{channelId}/unread-count", channelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastMessageSequence": 50,
                      "authoredSequences": []
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    private String createGuild(AuthSession owner) throws Exception {
        MvcResult guildResult = mockMvc.perform(post("/api/guilds")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Presence Guild"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(guildResult.getResponse().getContentAsString(), "$.id");
    }

    private String createChannel(String guildId, String name, AuthSession requester) throws Exception {
        MvcResult channelResult = mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "type": "GUILD_TEXT",
                      "parentId": null
                    }
                    """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(channelResult.getResponse().getContentAsString(), "$.id");
    }

    private void addMember(String guildId, UUID memberId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, memberId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());
    }

    private void denyEveryoneView(String guildId, String channelId, AuthSession owner) throws Exception {
        MvcResult rolesResult = mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andReturn();

        String everyoneRoleId = JsonPath.read(rolesResult.getResponse().getContentAsString(), "$[0].id");
        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                channelId,
                everyoneRoleId
            )
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allow": [],
                      "deny": ["VIEW_CHANNEL"]
                    }
                    """))
            .andExpect(status().isOk());
    }

    private AuthSession signup(String username) throws Exception {
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                .with(request -> {
                    request.setRemoteAddr(testClientIp(username));
                    return request;
                })
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

    private static String testClientIp(String username) {
        return "2001:db8::" + Integer.toHexString(username.hashCode());
    }

    private record AuthSession(String accessToken, UUID userId) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
