package com.example.discord.experience;

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

@SpringBootTest
@AutoConfigureMockMvc
class ExperienceControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void stageStartAndApprovalRequireModerator() throws Exception {
        AuthSession owner = signup("experience_stage_owner");
        AuthSession member = signup("experience_stage_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "stage", "GUILD_VOICE", owner);
        addMember(guildId, member.userId(), owner);

        mockMvc.perform(post("/api/stage/channels/{channelId}/sessions", channelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "topic": "Weekly stage"
                    }
                    """))
            .andExpect(status().isForbidden());

        MvcResult sessionResult = mockMvc.perform(post("/api/stage/channels/{channelId}/sessions", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "topic": "Weekly stage"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.topic").value("Weekly stage"))
            .andReturn();

        String sessionId = JsonPath.read(sessionResult.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(post("/api/stage/sessions/{sessionId}/request-to-speak", sessionId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pendingSpeakerIds[0]").value(member.userId().toString()));

        mockMvc.perform(put("/api/stage/sessions/{sessionId}/speakers/{userId}", sessionId, member.userId())
                .header("Authorization", member.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/stage/sessions/{sessionId}/speakers/{userId}", sessionId, member.userId())
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.speakerIds[0]").value(member.userId().toString()));
    }

    @Test
    void soundboardCreationRequiresManageExpressions() throws Exception {
        AuthSession owner = signup("experience_sound_owner");
        AuthSession member = signup("experience_sound_member");
        String guildId = createGuild(owner);
        addMember(guildId, member.userId(), owner);

        mockMvc.perform(post("/api/soundboard/guilds/{guildId}/sounds", guildId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "clap",
                      "objectKey": "sounds/clap.ogg"
                    }
                    """))
            .andExpect(status().isForbidden());

        grantRole(guildId, member.userId(), "sound_manager", "MANAGE_EXPRESSIONS", owner);

        mockMvc.perform(post("/api/soundboard/guilds/{guildId}/sounds", guildId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "clap",
                      "objectKey": "sounds/clap.ogg"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("clap"));
    }

    @Test
    void soundboardPlaybackValidatesChannelVisibility() throws Exception {
        AuthSession owner = signup("experience_play_owner");
        AuthSession outsider = signup("experience_play_outsider");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "voice", "GUILD_VOICE", owner);
        String soundId = createSound(guildId, "ping", owner);

        mockMvc.perform(post("/api/soundboard/channels/{channelId}/sounds/{soundId}/play", channelId, soundId)
                .header("Authorization", outsider.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/soundboard/channels/{channelId}/sounds/{soundId}/play", channelId, soundId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channelId").value(channelId))
            .andExpect(jsonPath("$.soundId").value(soundId));
    }

    @Test
    void premiumGateIsServerSide() throws Exception {
        AuthSession owner = signup("experience_premium_owner");
        String guildId = createGuild(owner);

        mockMvc.perform(get("/api/premium/users/{userId}/features/{featureKey}", owner.userId(), "hd_streaming")
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(post("/api/premium/users/{userId}/entitlements", owner.userId())
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "guildId": "%s",
                      "featureKey": "hd_streaming"
                    }
                    """.formatted(guildId)))
            .andExpect(status().isCreated());

        mockMvc.perform(get("/api/premium/users/{userId}/features/{featureKey}", owner.userId(), "hd_streaming")
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void stageMutationsPublishGatewayEventsToVisibleChannelSessions() throws Exception {
        AuthSession owner = signup("experience_gateway_stage_owner");
        AuthSession member = signup("experience_gateway_stage_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "stage-gateway", "GUILD_VOICE", owner);
        addMember(guildId, member.userId(), owner);
        GatewaySession memberGateway = identifyGateway(member);

        MvcResult sessionResult = mockMvc.perform(post("/api/stage/channels/{channelId}/sessions", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "topic": "Gateway stage"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String sessionId = JsonPath.read(sessionResult.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", memberGateway.sessionId())
                .header("Authorization", member.bearer())
                .param("afterSeq", Long.toString(memberGateway.readySequence())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].type").value("STAGE_SESSION_UPDATE"))
            .andExpect(jsonPath("$.events[0].guildId").value(guildId))
            .andExpect(jsonPath("$.events[0].channelId").value(channelId))
            .andExpect(jsonPath("$.events[0].payload.stageEventType").value("STAGE_SESSION_CREATED"))
            .andExpect(jsonPath("$.events[0].payload.sessionId").value(sessionId))
            .andExpect(jsonPath("$.events[0].payload.topic").value("Gateway stage"));

        mockMvc.perform(post("/api/stage/sessions/{sessionId}/request-to-speak", sessionId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", memberGateway.sessionId())
                .header("Authorization", member.bearer())
                .param("afterSeq", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].payload.stageEventType").value("STAGE_REQUEST_TO_SPEAK"))
            .andExpect(jsonPath("$.events[0].payload.pendingSpeakerIds[0]").value(member.userId().toString()));

        mockMvc.perform(put("/api/stage/sessions/{sessionId}/speakers/{userId}", sessionId, member.userId())
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", memberGateway.sessionId())
                .header("Authorization", member.bearer())
                .param("afterSeq", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].payload.stageEventType").value("STAGE_SPEAKER_APPROVED"))
            .andExpect(jsonPath("$.events[0].payload.speakerIds[0]").value(member.userId().toString()));
    }

    @Test
    void soundboardPlayPublishesGatewayEventToVisibleChannelSessions() throws Exception {
        AuthSession owner = signup("experience_gateway_sound_owner");
        AuthSession member = signup("experience_gateway_sound_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "sound-gateway", "GUILD_VOICE", owner);
        addMember(guildId, member.userId(), owner);
        String soundId = createSound(guildId, "rimshot", owner);
        GatewaySession memberGateway = identifyGateway(member);

        mockMvc.perform(post("/api/soundboard/channels/{channelId}/sounds/{soundId}/play", channelId, soundId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", memberGateway.sessionId())
                .header("Authorization", member.bearer())
                .param("afterSeq", Long.toString(memberGateway.readySequence())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(1)))
            .andExpect(jsonPath("$.events[0].type").value("SOUNDBOARD_SOUND_PLAY"))
            .andExpect(jsonPath("$.events[0].guildId").value(guildId))
            .andExpect(jsonPath("$.events[0].channelId").value(channelId))
            .andExpect(jsonPath("$.events[0].payload.soundId").value(soundId))
            .andExpect(jsonPath("$.events[0].payload.userId").value(owner.userId().toString()))
            .andExpect(jsonPath("$.events[0].payload.objectKey").doesNotExist());
    }

    @Test
    void stageAndSoundboardGatewayEventsAreNotDeliveredToHiddenChannelSessions() throws Exception {
        AuthSession owner = signup("experience_gateway_hidden_owner");
        AuthSession hiddenMember = signup("experience_gateway_hidden_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "hidden-media", "GUILD_VOICE", owner);
        addMember(guildId, hiddenMember.userId(), owner);
        denyEveryoneView(guildId, channelId, owner);
        String soundId = createSound(guildId, "secret", owner);
        GatewaySession hiddenGateway = identifyGateway(hiddenMember);

        mockMvc.perform(post("/api/stage/channels/{channelId}/sessions", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "topic": "Hidden stage"
                    }
                    """))
            .andExpect(status().isCreated());

        mockMvc.perform(post("/api/soundboard/channels/{channelId}/sounds/{soundId}/play", channelId, soundId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/gateway/sessions/{sessionId}/events", hiddenGateway.sessionId())
                .header("Authorization", hiddenMember.bearer())
                .param("afterSeq", Long.toString(hiddenGateway.readySequence())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.events", hasSize(0)));
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

    private String createChannel(String guildId, String name, String type, AuthSession requester) throws Exception {
        MvcResult channelResult = mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "type": "%s",
                      "parentId": null
                    }
                    """.formatted(name, type)))
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

    private void grantRole(String guildId, UUID memberId, String roleName, String permission, AuthSession owner) throws Exception {
        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "permissions": ["%s"]
                    }
                    """.formatted(roleName, permission)))
            .andExpect(status().isCreated())
            .andReturn();

        String roleId = JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id");
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, memberId, roleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());
    }

    private String createSound(String guildId, String name, AuthSession requester) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/soundboard/guilds/{guildId}/sounds", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "objectKey": "sounds/%s.ogg"
                    }
                    """.formatted(name, name)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
