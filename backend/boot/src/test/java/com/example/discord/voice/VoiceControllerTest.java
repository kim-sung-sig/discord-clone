package com.example.discord.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
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
class VoiceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void joinIssuesNonProductionTokenForVisibleVoiceChannel() throws Exception {
        AuthSession owner = signup("voice_token_owner");
        AuthSession member = signup("voice_token_member");
        String guildId = createGuild(owner);
        String channelId = createVoiceChannel(guildId, "General Voice", owner);
        addMember(guildId, member.userId(), owner);

        mockMvc.perform(post("/api/voice/channels/{channelId}/join", channelId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token.provider").value("LIVEKIT_SKELETON"))
            .andExpect(jsonPath("$.token.token", startsWith("NON_PRODUCTION_LIVEKIT_SKELETON:")))
            .andExpect(jsonPath("$.participant.guildId").value(guildId))
            .andExpect(jsonPath("$.participant.channelId").value(channelId))
            .andExpect(jsonPath("$.participant.userId").value(member.userId().toString()));
    }

    @Test
    void deniedVoiceChannelDoesNotIssueTokenOrCreateParticipantState() throws Exception {
        AuthSession owner = signup("voice_denied_owner");
        AuthSession member = signup("voice_denied_member");
        String guildId = createGuild(owner);
        String channelId = createVoiceChannel(guildId, "Staff Voice", owner);
        addMember(guildId, member.userId(), owner);
        denyEveryoneView(guildId, channelId, owner);

        mockMvc.perform(post("/api/voice/channels/{channelId}/join", channelId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/voice/channels/{channelId}/participants", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void leaveRemovesParticipantState() throws Exception {
        AuthSession owner = signup("voice_leave_owner");
        String guildId = createGuild(owner);
        String channelId = createVoiceChannel(guildId, "Leave Voice", owner);
        join(channelId, owner);

        mockMvc.perform(get("/api/voice/channels/{channelId}/participants", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].userId").value(owner.userId().toString()));

        mockMvc.perform(delete("/api/voice/channels/{channelId}/leave", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/voice/channels/{channelId}/participants", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").doesNotExist());
    }

    @Test
    void voiceEventsListIncludesStateUpdateRecords() throws Exception {
        AuthSession owner = signup("voice_events_owner");
        String guildId = createGuild(owner);
        String channelId = createVoiceChannel(guildId, "Events Voice", owner);
        join(channelId, owner);

        mockMvc.perform(patch("/api/voice/channels/{channelId}/state", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "muted": true,
                      "deafened": true,
                      "speaking": true,
                      "screenSharing": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.muted").value(true))
            .andExpect(jsonPath("$.deafened").value(true))
            .andExpect(jsonPath("$.speaking").value(true))
            .andExpect(jsonPath("$.screenSharing").value(true));

        MvcResult events = mockMvc.perform(get("/api/voice/events")
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andReturn();

        List<Map<String, Object>> body = JsonPath.read(events.getResponse().getContentAsString(), "$[*]");
        assertThat(body).anySatisfy(event -> {
            assertThat(event.get("type")).isEqualTo("VOICE_STATE_UPDATE");
            assertThat(event.get("guildId")).isEqualTo(guildId);
            assertThat(event.get("channelId")).isEqualTo(channelId);
            assertThat(event.get("userId")).isEqualTo(owner.userId().toString());
            assertThat(event.get("muted")).isEqualTo(true);
            assertThat(event.get("deafened")).isEqualTo(true);
            assertThat(event.get("speaking")).isEqualTo(true);
            assertThat(event.get("screenSharing")).isEqualTo(true);
        });
    }

    @Test
    void voiceMutationsPublishGatewayEventsOnlyToChannelVisibleSessions() throws Exception {
        AuthSession owner = signup("voice_gateway_owner");
        AuthSession hiddenMember = signup("voice_gateway_hidden_member");
        String guildId = createGuild(owner);
        String channelId = createVoiceChannel(guildId, "Gateway Voice", owner);
        addMember(guildId, hiddenMember.userId(), owner);
        denyEveryoneView(guildId, channelId, owner);
        GatewaySession ownerGateway = identifyGateway(owner);
        GatewaySession hiddenGateway = identifyGateway(hiddenMember);

        join(channelId, owner);

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", ownerGateway.sessionId())
                .header("Authorization", owner.bearer())
                .param("afterSeq", Long.toString(ownerGateway.readySequence())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].type").value("VOICE_STATE_UPDATE"))
            .andExpect(jsonPath("$.events[0].guildId").value(guildId))
            .andExpect(jsonPath("$.events[0].channelId").value(channelId))
            .andExpect(jsonPath("$.events[0].payload.voiceEventType").value("VOICE_STATE_JOINED"))
            .andExpect(jsonPath("$.events[0].payload.userId").value(owner.userId().toString()))
            .andExpect(jsonPath("$.events[0].payload.token").doesNotExist());

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", hiddenGateway.sessionId())
                .header("Authorization", hiddenMember.bearer())
                .param("afterSeq", Long.toString(hiddenGateway.readySequence())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(0)));

        mockMvc.perform(patch("/api/voice/channels/{channelId}/state", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "muted": true,
                      "deafened": false,
                      "speaking": true,
                      "screenSharing": false
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", ownerGateway.sessionId())
                .header("Authorization", owner.bearer())
                .param("afterSeq", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].payload.voiceEventType").value("VOICE_STATE_UPDATE"))
            .andExpect(jsonPath("$.events[0].payload.muted").value(true))
            .andExpect(jsonPath("$.events[0].payload.speaking").value(true));

        mockMvc.perform(delete("/api/voice/channels/{channelId}/leave", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", ownerGateway.sessionId())
                .header("Authorization", owner.bearer())
                .param("afterSeq", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].payload.voiceEventType").value("VOICE_STATE_LEFT"))
            .andExpect(jsonPath("$.events[0].payload.userId").value(owner.userId().toString()));
    }

    private void join(String channelId, AuthSession requester) throws Exception {
        mockMvc.perform(post("/api/voice/channels/{channelId}/join", channelId)
                .header("Authorization", requester.bearer()))
            .andExpect(status().isOk());
    }

    private GatewaySession identifyGateway(AuthSession requester) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/gateway/identify")
                .header("Authorization", requester.bearer()))
            .andExpect(status().isOk())
            .andReturn();

        String body = result.getResponse().getContentAsString();
        return new GatewaySession(
            JsonPath.read(body, "$.session.id"),
            ((Number) JsonPath.read(body, "$.ready.sequence")).longValue()
        );
    }

    private String createGuild(AuthSession owner) throws Exception {
        MvcResult guildResult = mockMvc.perform(post("/api/guilds")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Discord Clone"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(guildResult.getResponse().getContentAsString(), "$.id");
    }

    private String createVoiceChannel(String guildId, String name, AuthSession requester) throws Exception {
        MvcResult channelResult = mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "type": "GUILD_VOICE",
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
        MvcResult rolesResult = mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId))
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

    private record GatewaySession(String sessionId, long readySequence) {
    }
}
