package com.example.discord.message;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class MessageControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MessagePublicationOutboxQueue outboxQueue;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MessagePublicationRelayWorker relayWorker;

    @Test
    void exposesMessagePublicationOutboxBacklogMetric() {
        assertThat(meterRegistry.find("discord.message.publication.outbox.backlog").gauge()).isNotNull();
    }

    @Test
    void recordsOutboxRelayDurationAndOutcome() {
        relayWorker.relayPendingPublications();

        assertThat(meterRegistry.find("discord.message.publication.relay").tag("outcome", "published").timer())
            .isNotNull()
            .satisfies(timer -> assertThat(timer.count()).isPositive());
    }

    @Test
    void propagatesRequestIdIntoMessagePublicationCorrelationId() throws Exception {
        AuthSession owner = signup("message_correlation_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        String correlationId = "pipeline-" + UUID.randomUUID();

        MvcResult result = mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer())
                .header("X-Request-Id", correlationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "trace this publication",
                      "idempotencyKey": "trace-%s"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andReturn();
        UUID messageId = UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));

        List<ClaimedMessagePublication> publications = outboxQueue.claimPendingPublications(
            100,
            Instant.now(),
            Duration.ofSeconds(30)
        );

        assertThat(publications).anySatisfy(publication -> {
            assertThat(publication.event().messageId()).isEqualTo(messageId);
            assertThat(publication.event().correlationId()).isEqualTo(correlationId);
        });
    }

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
                      "content": "cannot send",
                      "idempotencyKey": "send-denied"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsCreateWithoutIdempotencyKey() throws Exception {
        AuthSession owner = signup("message_idempotency_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "missing idempotency key"
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reusesMessageForSameIdempotencyKeyRetry() throws Exception {
        AuthSession owner = signup("message_idempotent_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        String key = "send-" + UUID.randomUUID();

        String firstId = createMessage(channelId, "hello retry", key, owner);
        String secondId = createMessage(channelId, "hello retry", key, owner);

        mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages", hasSize(1)))
            .andExpect(jsonPath("$.messages[0].id").value(firstId))
            .andExpect(jsonPath("$.messages[0].sequence").value(1));
        org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentContent() throws Exception {
        AuthSession owner = signup("msg_idem_conflict_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        String key = "send-" + UUID.randomUUID();

        createMessage(channelId, "first content", key, owner);

        mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "second content",
                      "idempotencyKey": "%s"
                    }
                    """.formatted(key)))
            .andExpect(status().isConflict());
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
            .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    void exposesAuthorDisplayNameOnEveryMessageResponse() throws Exception {
        AuthSession owner = signup("message_name_owner", "Owner Display");
        AuthSession member = signup("message_name_member", "Member Display");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, member.userId(), owner);
        grantRole(guildId, member.userId(), "name_sender", "SEND_MESSAGES", owner);

        MvcResult created = mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "owner hello",
                      "idempotencyKey": "name-%s"
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.authorId").value(owner.userId().toString()))
            .andExpect(jsonPath("$.authorDisplayName").value("Owner Display"))
            .andReturn();
        String ownerMessageId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");
        String memberMessageId = createMessage(channelId, "member hello", member);

        MvcResult latestPage = mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer())
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages", hasSize(1)))
            .andExpect(jsonPath("$.nextCursor").isString())
            .andReturn();
        String latestBody = latestPage.getResponse().getContentAsString();
        MvcResult olderPage = mockMvc.perform(get("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer())
                .param("before", JsonPath.<String>read(latestBody, "$.nextCursor"))
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.messages", hasSize(1)))
            .andReturn();
        String olderBody = olderPage.getResponse().getContentAsString();
        assertThat(java.util.Map.of(
            JsonPath.<String>read(latestBody, "$.messages[0].id"),
            JsonPath.<String>read(latestBody, "$.messages[0].authorDisplayName"),
            JsonPath.<String>read(olderBody, "$.messages[0].id"),
            JsonPath.<String>read(olderBody, "$.messages[0].authorDisplayName")
        )).containsOnly(
            java.util.Map.entry(ownerMessageId, "Owner Display"),
            java.util.Map.entry(memberMessageId, "Member Display")
        );

        mockMvc.perform(patch("/api/channels/{channelId}/messages/{messageId}", channelId, memberMessageId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "member hello edited"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorDisplayName").value("Member Display"));

        mockMvc.perform(put("/api/channels/{channelId}/messages/{messageId}/pin", channelId, memberMessageId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorId").value(member.userId().toString()))
            .andExpect(jsonPath("$.authorDisplayName").value("Member Display"));

        mockMvc.perform(delete("/api/channels/{channelId}/messages/{messageId}/pin", channelId, memberMessageId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authorDisplayName").value("Member Display"));

        mockMvc.perform(get("/api/channels/{channelId}/messages/search", channelId)
                .header("Authorization", owner.bearer())
                .param("q", "member hello"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].id").value(memberMessageId))
            .andExpect(jsonPath("$[0].authorDisplayName").value("Member Display"));
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
        return createMessage(channelId, content, "send-" + UUID.randomUUID(), requester);
    }

    private String createMessage(String channelId, String content, String idempotencyKey, AuthSession requester) throws Exception {
        MvcResult messageResult = mockMvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "%s",
                      "idempotencyKey": "%s"
                    }
                    """.formatted(content, idempotencyKey)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.sequence").isNumber())
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
        return signup(username, username);
    }

    private AuthSession signup(String username, String displayName) throws Exception {
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s@example.com",
                      "username": "%s",
                      "displayName": "%s",
                      "password": "correct horse battery staple"
                    }
                    """.formatted(username, username, displayName)))
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
