package com.example.discord.thread;

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
class ThreadControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void inheritsParentViewAndSendPermissionsWhenCreatingThread() throws Exception {
        AuthSession owner = signup("thread_parent_owner");
        AuthSession member = signup("thread_parent_member");
        String guildId = createGuild(owner);
        String visibleChannelId = createChannel(guildId, "visible-parent", "GUILD_TEXT", owner);
        String hiddenChannelId = createChannel(guildId, "hidden-parent", "GUILD_TEXT", owner);
        addMember(guildId, member.userId(), owner);
        grantRole(guildId, member.userId(), "thread_parent_sender", "SEND_MESSAGES", owner);
        denyEveryoneView(guildId, hiddenChannelId, owner);

        mockMvc.perform(post("/api/channels/{channelId}/threads", hiddenChannelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(threadRequest("hidden thread")))
            .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/channels/{channelId}/threads", visibleChannelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(threadRequest("visible thread")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.parentChannelId").value(visibleChannelId))
            .andExpect(jsonPath("$.archived").value(false));
    }

    @Test
    void rejectsWritesToArchivedThread() throws Exception {
        AuthSession owner = signup("thread_archive_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "archive-parent", "GUILD_TEXT", owner);
        String threadId = createThread(channelId, "archive me", owner);

        mockMvc.perform(put("/api/threads/{threadId}/archive", threadId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(post("/api/threads/{threadId}/messages", threadId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "blocked"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void requiresForumPostTag() throws Exception {
        AuthSession owner = signup("thread_forum_owner");
        String guildId = createGuild(owner);
        String forumChannelId = createChannel(guildId, "help-forum", "GUILD_FORUM", owner);

        mockMvc.perform(post("/api/channels/{channelId}/forum-posts", forumChannelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "How do I ship this?",
                      "tagIds": []
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createsForumTagAndTaggedForumPostOnlyOnForumChannels() throws Exception {
        AuthSession owner = signup("thread_forum_tag_owner");
        String guildId = createGuild(owner);
        String textChannelId = createChannel(guildId, "not-a-forum", "GUILD_TEXT", owner);
        String forumChannelId = createChannel(guildId, "release-forum", "GUILD_FORUM", owner);

        mockMvc.perform(post("/api/channels/{channelId}/forum-tags", textChannelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "release"
                    }
                    """))
            .andExpect(status().isBadRequest());

        MvcResult tagResult = mockMvc.perform(post("/api/channels/{channelId}/forum-tags", forumChannelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "release"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.forumChannelId").value(forumChannelId))
            .andExpect(jsonPath("$.name").value("release"))
            .andReturn();

        String tagId = JsonPath.read(tagResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/channels/{channelId}/forum-posts", forumChannelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "title": "Release plan",
                      "tagIds": ["%s"],
                      "autoArchiveMinutes": 60
                    }
                    """.formatted(tagId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.thread.parentChannelId").value(forumChannelId))
            .andExpect(jsonPath("$.tagIds[0]").value(tagId));
    }

    @Test
    void archiveExpiredRequiresBearerToken() throws Exception {
        AuthSession user = signup("thread_archive_expired_user");

        mockMvc.perform(post("/api/threads/archive-expired"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/threads/archive-expired")
                .header("Authorization", user.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.archivedCount").value(0));
    }

    private String createGuild(AuthSession owner) throws Exception {
        MvcResult guildResult = mockMvc.perform(post("/api/guilds")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Thread Guild"
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

    private String createThread(String channelId, String name, AuthSession requester) throws Exception {
        MvcResult threadResult = mockMvc.perform(post("/api/channels/{channelId}/threads", channelId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(threadRequest(name)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(threadResult.getResponse().getContentAsString(), "$.id");
    }

    private String threadRequest(String name) {
        return """
            {
              "name": "%s",
              "type": "PUBLIC",
              "autoArchiveMinutes": 60
            }
            """.formatted(name);
    }

    private void addMember(String guildId, UUID memberId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, memberId)
                .header("Authorization", owner.bearer()))
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
                .header("X-Forwarded-For", testClientIp(username))
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
