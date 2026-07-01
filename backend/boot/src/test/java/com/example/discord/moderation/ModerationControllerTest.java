package com.example.discord.moderation;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class ModerationControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void onboardingAnswerAssignsConfiguredRole() throws Exception {
        AuthSession owner = signup("moderation_onboarding_owner");
        AuthSession member = signup("moderation_onboarding_member");
        String guildId = createGuild(owner);
        addMember(guildId, member.userId(), owner);
        String roleId = createRole(guildId, "onboarding-news", owner);

        MvcResult questionResult = mockMvc.perform(post("/api/guilds/{guildId}/onboarding/questions", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "prompt": "Choose updates",
                      "answers": [
                        {
                          "label": "Announcements",
                          "roleGrantIds": ["%s"]
                        }
                      ]
                    }
                    """.formatted(roleId)))
            .andExpect(status().isCreated())
            .andReturn();

        String answerId = JsonPath.read(questionResult.getResponse().getContentAsString(), "$.answers[0].id");

        mockMvc.perform(post("/api/guilds/{guildId}/onboarding/answers", guildId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "answerIds": ["%s"]
                    }
                    """.formatted(answerId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId").value(member.userId().toString()))
            .andExpect(jsonPath("$.roleIds", hasItem(roleId)));
    }

    @Test
    void autoModBlockedMessageReturnsForbiddenAndIsAbsentFromMessageList() throws Exception {
        AuthSession owner = signup("moderation_block_owner");
        AuthSession member = signup("moderation_block_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, member.userId(), owner);
        grantRole(guildId, member.userId(), "sender", "SEND_MESSAGES", owner);
        createKeywordRule(guildId, "spoiler", owner);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "this has a spoiler",
                      "idempotencyKey": "send-spoiler"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    void autoModBlockCreatesSecurityAlertWithoutPersistingMessage() throws Exception {
        AuthSession owner = signup("moderation_alert_owner");
        AuthSession member = signup("moderation_alert_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "alerts", owner);
        addMember(guildId, member.userId(), owner);
        grantRole(guildId, member.userId(), "alert-sender", "SEND_MESSAGES", owner);
        createKeywordRule(guildId, "malware", owner);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "malware link",
                      "idempotencyKey": "send-malware"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/guilds/{guildId}/security-alerts", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].type").value("AUTOMOD_BLOCK"))
            .andExpect(jsonPath("$[0].severity").value("MEDIUM"))
            .andExpect(jsonPath("$[0].actorId").value(member.userId().toString()))
            .andExpect(jsonPath("$[0].targetId").exists())
            .andExpect(jsonPath("$[0].createdAt").exists());

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    void autoModRuleCreationAppendsAuditLog() throws Exception {
        AuthSession owner = signup("moderation_audit_owner");
        String guildId = createGuild(owner);

        MvcResult ruleResult = createKeywordRule(guildId, "spoiler", owner);
        String ruleId = JsonPath.read(ruleResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/guilds/{guildId}/audit-logs", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].action").value("AUTOMOD_RULE_CREATED"))
            .andExpect(jsonPath("$[0].actorId").value(owner.userId().toString()))
            .andExpect(jsonPath("$[0].targetId").value(ruleId));
    }

    @Test
    void privilegedActionsAreSearchableInAuditLog() throws Exception {
        AuthSession owner = signup("moderation_search_owner");
        AuthSession member = signup("moderation_search_member");
        String guildId = createGuild(owner);
        String textChannelId = createChannel(guildId, "audit-general", "GUILD_TEXT", owner);
        String voiceChannelId = createChannel(guildId, "audit-stage", "GUILD_VOICE", owner);
        addMember(guildId, member.userId(), owner);

        String roleId = createRole(guildId, "audit-role", owner);
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, member.userId(), roleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        String inviteCode = createInvite(guildId, textChannelId, owner);
        mockMvc.perform(delete("/api/invites/{code}", inviteCode)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());

        String messageId = createMessage(textChannelId, "moderate me", owner);
        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", textChannelId, messageId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());

        String stageSessionId = startStageSession(voiceChannelId, owner);
        mockMvc.perform(post("/api/stage/sessions/{sessionId}/request-to-speak", stageSessionId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk());
        mockMvc.perform(put("/api/stage/sessions/{sessionId}/speakers/{userId}", stageSessionId, member.userId())
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        assertAuditAction(guildId, owner, "ROLE_ASSIGNED", member.userId().toString());
        assertAuditAction(guildId, owner, "INVITE_DELETED", textChannelId);
        assertAuditAction(guildId, owner, "MESSAGE_DELETED", messageId);
        assertAuditAction(guildId, owner, "STAGE_SPEAKER_APPROVED", member.userId().toString());

        mockMvc.perform(get("/api/guilds/{guildId}/audit-logs", guildId)
                .header("Authorization", owner.bearer())
                .param("actorId", owner.userId().toString())
                .param("action", "MESSAGE_DELETED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].action").value("MESSAGE_DELETED"))
            .andExpect(jsonPath("$[0].actorId").value(owner.userId().toString()));
    }

    @Test
    void memberCanReportMessageAndModeratorCanResolveIt() throws Exception {
        AuthSession owner = signup("moderation_report_owner");
        AuthSession member = signup("moderation_report_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "reports", owner);
        addMember(guildId, member.userId(), owner);
        grantRole(guildId, member.userId(), "report-sender", "SEND_MESSAGES", owner);
        String messageId = createMessage(channelId, "bad message", member);

        MvcResult reportResult = mockMvc.perform(post(
                    "/api/guilds/{guildId}/channels/{channelId}/messages/{messageId}/reports",
                    guildId,
                    channelId,
                    messageId
                )
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reason": "harassment"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.messageId").value(messageId))
            .andExpect(jsonPath("$.reporterId").value(member.userId().toString()))
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn();

        String reportId = JsonPath.read(reportResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/guilds/{guildId}/message-reports", guildId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/guilds/{guildId}/message-reports", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(reportId));

        mockMvc.perform(post("/api/guilds/{guildId}/message-reports/{reportId}/resolve", guildId, reportId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "status": "RESOLVED",
                      "resolution": "message removed"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("RESOLVED"))
            .andExpect(jsonPath("$.moderatorId").value(owner.userId().toString()));

        mockMvc.perform(get("/api/guilds/{guildId}/message-reports", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());

        assertAuditAction(guildId, owner, "MESSAGE_REPORTED", member.userId().toString(), messageId);
        assertAuditAction(guildId, owner, "MESSAGE_REPORT_RESOLVED", messageId);
    }

    private MvcResult createKeywordRule(String guildId, String keyword, AuthSession requester) throws Exception {
        return mockMvc.perform(post("/api/guilds/{guildId}/automod/rules", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "KEYWORD",
                      "name": "blocked words",
                      "keywords": ["%s"]
                    }
                    """.formatted(keyword)))
            .andExpect(status().isCreated())
            .andReturn();
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
        return createChannel(guildId, name, "GUILD_TEXT", requester);
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

    private String createRole(String guildId, String name, AuthSession requester) throws Exception {
        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "permissions": []
                    }
                    """.formatted(name)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id");
    }

    private String createInvite(String guildId, String channelId, AuthSession requester) throws Exception {
        MvcResult inviteResult = mockMvc.perform(post("/api/guilds/{guildId}/invites", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "maxAgeSeconds": 3600,
                      "maxUses": 10,
                      "temporary": false,
                      "roleGrantIds": []
                    }
                    """.formatted(channelId)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(inviteResult.getResponse().getContentAsString(), "$.code");
    }

    private String createMessage(String channelId, String content, AuthSession requester) throws Exception {
        MvcResult messageResult = mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "%s",
                      "idempotencyKey": "send-%s"
                    }
                    """.formatted(content, UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(messageResult.getResponse().getContentAsString(), "$.id");
    }

    private String startStageSession(String channelId, AuthSession requester) throws Exception {
        MvcResult sessionResult = mockMvc.perform(post("/api/stage/channels/{channelId}/sessions", channelId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "topic": "Audit stage"
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(sessionResult.getResponse().getContentAsString(), "$.id");
    }

    private void assertAuditAction(String guildId, AuthSession requester, String action, String targetId) throws Exception {
        assertAuditAction(guildId, requester, action, requester.userId().toString(), targetId);
    }

    private void assertAuditAction(
        String guildId,
        AuthSession requester,
        String action,
        String actorId,
        String targetId
    ) throws Exception {
        mockMvc.perform(get("/api/guilds/{guildId}/audit-logs", guildId)
                .header("Authorization", requester.bearer())
                .param("action", action)
                .param("targetId", targetId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].action").value(action))
            .andExpect(jsonPath("$[0].actorId").value(actorId))
            .andExpect(jsonPath("$[0].targetId").value(targetId))
            .andExpect(jsonPath("$[0].createdAt").exists());
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

    private void addMember(String guildId, UUID memberId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, memberId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());
    }

    private AuthSession signup(String username) throws Exception {
        String uniqueUsername = "mod_%s".formatted(UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s@example.com",
                      "username": "%s",
                      "displayName": "%s",
                      "password": "correct horse battery staple"
                    }
                    """.formatted(uniqueUsername, uniqueUsername, uniqueUsername)))
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
