package com.example.discord.gateway;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
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

@SpringBootTest(properties = "discord.gateway.internal-publisher-token=test-harness")
@AutoConfigureMockMvc
class GatewayControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void identifyReturnsReadyForAuthenticatedUserGuilds() throws Exception {
        AuthSession owner = signup("gateway_identify_owner");
        String guildId = createGuild(owner);

        MvcResult result = identify(owner)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.session.userId").value(owner.userId().toString()))
            .andExpect(jsonPath("$.session.guildIds", contains(guildId)))
            .andExpect(jsonPath("$.ready.type").value("READY"))
            .andExpect(jsonPath("$.ready.sequence", greaterThan(0)))
            .andReturn();

        String sessionId = JsonPath.read(result.getResponse().getContentAsString(), "$.session.id");
        mockMvc.perform(post("/api/gateway/sessions/{sessionId}/heartbeat", sessionId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ack.type").value("HEARTBEAT_ACK"));
    }

    @Test
    void heartbeatRequiresSessionOwner() throws Exception {
        AuthSession owner = signup("gateway_owner_check_owner");
        AuthSession other = signup("gateway_owner_check_other");
        createGuild(owner);
        String sessionId = sessionId(identify(owner).andExpect(status().isOk()).andReturn());

        mockMvc.perform(post("/api/gateway/sessions/{sessionId}/heartbeat", sessionId)
                .header("Authorization", other.bearer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void resumeReturnsEventsAfterLastSequence() throws Exception {
        AuthSession owner = signup("gateway_resume_owner");
        String guildId = createGuild(owner);
        String sessionId = sessionId(identify(owner).andExpect(status().isOk()).andReturn());
        long before = publish(owner, "GUILD_UPDATE", guildId, null, "before");
        long after = publish(owner, "GUILD_UPDATE", guildId, null, "after");

        mockMvc.perform(post("/api/gateway/sessions/{sessionId}/resume", sessionId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "lastSeq": %d
                    }
                    """.formatted(before)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumed.type").value("RESUMED"))
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].sequence").value((int) after));
    }

    @Test
    void eventsEndpointPollsPublishedEvents() throws Exception {
        AuthSession owner = signup("gateway_poll_owner");
        String guildId = createGuild(owner);
        String sessionId = sessionId(identify(owner).andExpect(status().isOk()).andReturn());
        long eventSeq = publish(owner, "GUILD_UPDATE", guildId, null, "poll");

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", sessionId)
                .header("Authorization", owner.bearer())
                .param("afterSeq", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].sequence").value((int) eventSeq))
            .andExpect(jsonPath("$.events[0].payload.content").value("poll"));
    }

    @Test
    void eventPollingFiltersHiddenChannelEvents() throws Exception {
        AuthSession owner = signup("gateway_hidden_owner");
        AuthSession member = signup("gateway_hidden_member");
        String guildId = createGuild(owner);
        String visibleChannelId = createChannel(guildId, "general", owner);
        String hiddenChannelId = createChannel(guildId, "staff", owner);
        addMember(guildId, member.userId(), owner);
        denyEveryoneView(guildId, hiddenChannelId, owner);
        String sessionId = sessionId(identify(member).andExpect(status().isOk()).andReturn());
        long visibleSeq = publish(owner, "MESSAGE_CREATE", guildId, visibleChannelId, "visible");
        publish(owner, "MESSAGE_CREATE", guildId, hiddenChannelId, "hidden");

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", sessionId)
                .header("Authorization", member.bearer())
                .param("afterSeq", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].sequence").value((int) visibleSeq))
            .andExpect(jsonPath("$.events[0].payload.content").value("visible"));
    }

    @Test
    void rejectsPublishForNonMemberAndMismatchedChannel() throws Exception {
        AuthSession owner = signup("gateway_publish_owner");
        AuthSession outsider = signup("gateway_publish_outsider");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);

        mockMvc.perform(post("/api/gateway/events")
                .header("Authorization", outsider.bearer())
                .header("X-Internal-Gateway-Publisher", "test-harness")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "MESSAGE_CREATE",
                      "guildId": "%s",
                      "channelId": "%s",
                      "payload": {
                        "content": "spoof"
                      }
                    }
                    """.formatted(guildId, channelId)))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/gateway/events")
                .header("Authorization", owner.bearer())
                .header("X-Internal-Gateway-Publisher", "test-harness")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "MESSAGE_CREATE",
                      "guildId": "%s",
                      "channelId": "%s",
                      "payload": {
                        "content": "mismatch"
                      }
                    }
                    """.formatted(guildId, UUID.randomUUID())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPublicGatewayPublishWithoutInternalGatewayHeader() throws Exception {
        AuthSession owner = signup("gateway_publish_public_owner");
        String guildId = createGuild(owner);

        mockMvc.perform(post("/api/gateway/events")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "MESSAGE_CREATE",
                      "guildId": "%s",
                      "channelId": null,
                      "payload": {
                        "content": "spoof"
                      }
                    }
                    """.formatted(guildId)))
            .andExpect(status().isForbidden());
    }

    private org.springframework.test.web.servlet.ResultActions identify(AuthSession user) throws Exception {
        return mockMvc.perform(post("/api/gateway/identify")
            .header("Authorization", user.bearer()));
    }

    private long publish(AuthSession requester, String type, String guildId, String channelId, String content) throws Exception {
        String channelJson = channelId == null ? "null" : "\"" + channelId + "\"";
        MvcResult result = mockMvc.perform(post("/api/gateway/events")
                .header("Authorization", requester.bearer())
                .header("X-Internal-Gateway-Publisher", "test-harness")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "%s",
                      "guildId": "%s",
                      "channelId": %s,
                      "payload": {
                        "content": "%s"
                      }
                    }
                    """.formatted(type, guildId, channelJson, content)))
            .andExpect(status().isCreated())
            .andReturn();

        Number sequence = JsonPath.read(result.getResponse().getContentAsString(), "$.sequence");
        return sequence.longValue();
    }

    private String sessionId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.session.id");
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
}
