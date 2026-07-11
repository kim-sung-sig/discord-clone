package com.example.discord.expression;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ExpressionControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ExpressionService expressionService;

    private final List<Sticker> stickers = new ArrayList<>();
    private final Set<UUID> reactionUsers = new LinkedHashSet<>();

    @BeforeEach
    void setUpExpressionService() {
        doAnswer(invocation -> new CustomEmoji(
            UUID.randomUUID(), invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3)
        )).when(expressionService).createCustomEmoji(any(), any(), any(), any());
        doAnswer(invocation -> {
            Sticker sticker = new Sticker(UUID.randomUUID(), invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2), invocation.getArgument(3));
            stickers.add(sticker);
            return sticker;
        }).when(expressionService).createSticker(any(), any(), any(), any());
        when(expressionService.stickers(any())).thenAnswer(invocation -> List.copyOf(stickers));
        doAnswer(invocation -> {
            reactionUsers.add(invocation.getArgument(3));
            return null;
        }).when(expressionService).addReaction(any(), any(), any(), any());
        when(expressionService.reactionSummaries(any(), any())).thenAnswer(invocation ->
            List.of(new ReactionSummary("wave", reactionUsers.size(), Set.copyOf(reactionUsers)))
        );
    }

    @Test
    void customEmojiCreationRequiresManageExpressions() throws Exception {
        AuthSession owner = signup("expression_emoji_owner");
        AuthSession member = signup("expression_emoji_member");
        String guildId = createGuild(owner);
        addMember(guildId, member.userId(), owner);

        mockMvc.perform(post("/api/guilds/{guildId}/emojis", guildId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "shipit",
                      "imageObjectKey": "emoji/shipit.png"
                    }
                    """))
            .andExpect(status().isForbidden());

        grantRole(guildId, member.userId(), "expression_manager", "MANAGE_EXPRESSIONS", owner);

        mockMvc.perform(post("/api/guilds/{guildId}/emojis", guildId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "shipit",
                      "imageObjectKey": "emoji/shipit.png"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("shipit"))
            .andExpect(jsonPath("$.imageObjectKey").value("emoji/shipit.png"));
    }

    @Test
    void duplicateReactionAddIsIdempotentOverRest() throws Exception {
        AuthSession owner = signup("expression_reaction_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        String messageId = createMessage(channelId, "react here", owner);

        mockMvc.perform(put(
                "/api/channels/{channelId}/messages/{messageId}/reactions/{emojiKey}",
                channelId,
                messageId,
                "wave"
            )
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.emojiKey").value("wave"))
            .andExpect(jsonPath("$.count").value(1))
            .andExpect(jsonPath("$.currentUserReacted").value(true));

        mockMvc.perform(put(
                "/api/channels/{channelId}/messages/{messageId}/reactions/{emojiKey}",
                channelId,
                messageId,
                "wave"
            )
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));

        mockMvc.perform(get("/api/channels/{channelId}/messages/{messageId}/reactions", channelId, messageId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].emojiKey").value("wave"))
            .andExpect(jsonPath("$[0].count").value(1))
            .andExpect(jsonPath("$[0].currentUserReacted").value(true));
    }

    @Test
    void rejectsReactionForMissingMessage() throws Exception {
        AuthSession owner = signup("expression_missing_message_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        String missingMessageId = UUID.randomUUID().toString();

        mockMvc.perform(put(
                "/api/channels/{channelId}/messages/{messageId}/reactions/{emojiKey}",
                channelId,
                missingMessageId,
                "wave"
            )
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNotFound());
    }

    @Test
    void createsAndListsStickerSkeletons() throws Exception {
        AuthSession owner = signup("expression_sticker_owner");
        String guildId = createGuild(owner);

        mockMvc.perform(post("/api/guilds/{guildId}/stickers", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "approved",
                      "description": "approved sticker"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("approved"))
            .andExpect(jsonPath("$.description").value("approved sticker"));

        mockMvc.perform(get("/api/guilds/{guildId}/stickers", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("approved"));
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
                      "content": "%s",
                      "idempotencyKey": "send-%s"
                    }
                    """.formatted(content, UUID.randomUUID())))
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
