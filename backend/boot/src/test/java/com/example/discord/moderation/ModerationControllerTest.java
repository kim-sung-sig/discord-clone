package com.example.discord.moderation;

import static org.hamcrest.Matchers.hasItem;
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
                      "content": "this has a spoiler"
                    }
                    """))
            .andExpect(status().isForbidden());

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
