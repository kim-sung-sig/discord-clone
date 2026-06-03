package com.example.discord.message;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class MessageControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsCreateWithoutSendMessages() throws Exception {
        AuthSession owner = signup("message_send_owner");
        AuthSession member = signup("message_send_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, member.userId(), owner);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "cannot send"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsListAndSearchWithoutViewChannel() throws Exception {
        AuthSession owner = signup("message_view_owner");
        AuthSession member = signup("message_view_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "staff", owner);
        addMember(guildId, member.userId(), owner);
        denyEveryoneView(guildId, channelId, owner);

        createMessage(channelId, "staff note", owner);

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/channels/{channelId}/messages/search", channelId)
                .header("Authorization", member.bearer())
                .param("q", "staff"))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsAuthorToEditAndDeleteOwnMessage() throws Exception {
        AuthSession owner = signup("message_author_owner");
        AuthSession author = signup("message_author_member");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, author.userId(), owner);
        grantRole(guildId, author.userId(), "author_sender", "SEND_MESSAGES", owner);
        String messageId = createMessage(channelId, "before", author);

        mockMvc.perform(patch("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                .header("Authorization", author.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "after <@00000000-0000-0000-0000-000000000099>"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content").value("after <@00000000-0000-0000-0000-000000000099>"))
            .andExpect(jsonPath("$.edited").value(true))
            .andExpect(jsonPath("$.mentions").isEmpty());

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                .header("Authorization", author.bearer()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages[0].id").value(messageId))
            .andExpect(jsonPath("$.messages[0].deleted").value(true))
            .andExpect(jsonPath("$.messages[0].content").value("[deleted]"));
    }

    @Test
    void rejectsAuthorMutationAfterChannelViewIsRevoked() throws Exception {
        AuthSession owner = signup("message_revoked_owner");
        AuthSession author = signup("message_revoked_author");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "staff", owner);
        addMember(guildId, author.userId(), owner);
        grantRole(guildId, author.userId(), "revoked_sender", "SEND_MESSAGES", owner);
        String messageId = createMessage(channelId, "before revoke", author);
        denyEveryoneView(guildId, channelId, owner);

        mockMvc.perform(patch("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                .header("Authorization", author.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "after revoke"
                    }
                    """))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                .header("Authorization", author.bearer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsManagerToDeleteAndPinOthersMessages() throws Exception {
        AuthSession owner = signup("message_manage_owner");
        AuthSession author = signup("message_manage_author");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, author.userId(), owner);
        grantRole(guildId, author.userId(), "managed_sender", "SEND_MESSAGES", owner);
        String pinnedMessageId = createMessage(channelId, "pin this", author);
        String deletedMessageId = createMessage(channelId, "delete this", author);

        mockMvc.perform(put("/api/channels/{channelId}/messages/{messageId}/pin", channelId, pinnedMessageId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(true));

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}/pin", channelId, pinnedMessageId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(false));

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", channelId, deletedMessageId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());
    }

    @Test
    void rejectsChannelManagerWithoutManageMessagesForMessageModeration() throws Exception {
        AuthSession owner = signup("message_channel_manager_owner");
        AuthSession author = signup("message_channel_manager_author");
        AuthSession channelManager = signup("message_channel_manager");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, author.userId(), owner);
        addMember(guildId, channelManager.userId(), owner);
        grantRole(guildId, author.userId(), "channel_manager_sender", "SEND_MESSAGES", owner);
        grantRole(guildId, channelManager.userId(), "channel_manager_only", "MANAGE_CHANNELS", owner);
        String messageId = createMessage(channelId, "moderate me", author);

        mockMvc.perform(put("/api/channels/{channelId}/messages/{messageId}/pin", channelId, messageId)
                .header("Authorization", channelManager.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                .header("Authorization", channelManager.bearer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsManageMessagesRoleToModerateOthersMessages() throws Exception {
        AuthSession owner = signup("message_manager_owner");
        AuthSession author = signup("message_manager_author");
        AuthSession messageManager = signup("message_manager");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, author.userId(), owner);
        addMember(guildId, messageManager.userId(), owner);
        grantRole(guildId, author.userId(), "message_manager_sender", "SEND_MESSAGES", owner);
        grantRole(guildId, messageManager.userId(), "message_manager_role", "MANAGE_MESSAGES", owner);
        String messageId = createMessage(channelId, "moderate me", author);

        mockMvc.perform(put("/api/channels/{channelId}/messages/{messageId}/pin", channelId, messageId)
                .header("Authorization", messageManager.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pinned").value(true));

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}", channelId, messageId)
                .header("Authorization", messageManager.bearer()))
            .andExpect(status().isNoContent());
    }

    @Test
    void rejectsManageMessagesWithoutViewChannel() throws Exception {
        AuthSession owner = signup("message_hidden_manager_owner");
        AuthSession author = signup("message_hidden_manager_author");
        AuthSession messageManager = signup("message_hidden_manager");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "hidden", owner);
        addMember(guildId, author.userId(), owner);
        addMember(guildId, messageManager.userId(), owner);
        grantRole(guildId, author.userId(), "hidden_sender", "SEND_MESSAGES", owner);
        grantRole(guildId, messageManager.userId(), "hidden_message_manager", "MANAGE_MESSAGES", owner);
        String messageId = createMessage(channelId, "hidden text", author);
        denyEveryoneView(guildId, channelId, owner);

        mockMvc.perform(put("/api/channels/{channelId}/messages/{messageId}/pin", channelId, messageId)
                .header("Authorization", messageManager.bearer()))
            .andExpect(status().isForbidden());
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

    private String createMessage(String channelId, String content, AuthSession requester) throws Exception {
        MvcResult messageResult = mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "%s"
                    }
                    """.formatted(content)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(messageResult.getResponse().getContentAsString(), "$.id");
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
