package com.example.discord.invite;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
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
class InviteControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsInviteCreateFromMemberWithoutManageChannels() throws Exception {
        AuthSession owner = signup("invite_owner_no_manage");
        AuthSession member = signup("invite_member_no_manage");
        String guildId = createGuild(owner);
        addMember(guildId, member.userId(), owner);

        mockMvc.perform(post("/api/guilds/{guildId}/invites", guildId)
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": null,
                      "maxAgeSeconds": 0,
                      "maxUses": 0,
                      "temporary": false,
                      "roleGrantIds": []
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void acceptsInviteIdempotentlyAndGrantsConfiguredRoles() throws Exception {
        AuthSession owner = signup("invite_owner_idempotent");
        AuthSession member = signup("invite_member_idempotent");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, owner);
        String roleId = createRole(guildId, "invite_role", owner);
        String code = createInvite(guildId, channelId, List.of(roleId), owner, 0, 1);

        mockMvc.perform(post("/api/invites/{code}/accept", code)
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.guildId").value(guildId))
            .andExpect(jsonPath("$.memberId").value(member.userId().toString()))
            .andExpect(jsonPath("$.alreadyAccepted").value(false))
            .andExpect(jsonPath("$.roleIds", hasItem(roleId)));

        mockMvc.perform(post("/api/invites/{code}/accept", code)
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.alreadyAccepted").value(true))
            .andExpect(jsonPath("$.uses").value(1));
    }

    @Test
    void rejectsExpiredInviteAccept() throws Exception {
        AuthSession owner = signup("invite_owner_expired");
        AuthSession member = signup("invite_member_expired");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, owner);
        String code = createInvite(guildId, channelId, List.of(), owner, -1, 0);

        mockMvc.perform(post("/api/invites/{code}/accept", code)
                .header("Authorization", member.bearer()))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("invite expired"));
    }

    @Test
    void rejectsDeletedInviteReuse() throws Exception {
        AuthSession owner = signup("invite_owner_deleted");
        AuthSession member = signup("invite_member_deleted");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, owner);
        String code = createInvite(guildId, channelId, List.of(), owner, 0, 0);

        mockMvc.perform(delete("/api/invites/{code}", code)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/invites/{code}", code))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("invite deleted"));

        mockMvc.perform(post("/api/invites/{code}/accept", code)
                .header("Authorization", member.bearer()))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("invite deleted"));
    }

    @Test
    void rejectsDistinctAcceptAfterMaxUsesExceeded() throws Exception {
        AuthSession owner = signup("invite_owner_max_uses");
        AuthSession firstMember = signup("invite_member_max_uses_first");
        AuthSession secondMember = signup("invite_member_max_uses_second");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, owner);
        String code = createInvite(guildId, channelId, List.of(), owner, 0, 1);

        mockMvc.perform(post("/api/invites/{code}/accept", code)
                .header("Authorization", firstMember.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.uses").value(1));

        mockMvc.perform(post("/api/invites/{code}/accept", code)
                .header("Authorization", secondMember.bearer()))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.message").value("invite max uses exceeded"));
    }

    @Test
    void rejectsInviteCreateWithInvalidChannelOrRoleGrant() throws Exception {
        AuthSession owner = signup("invite_owner_invalid_grant");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, owner);

        mockMvc.perform(post("/api/guilds/{guildId}/invites", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "maxAgeSeconds": 0,
                      "maxUses": 0,
                      "temporary": false,
                      "roleGrantIds": []
                    }
                    """.formatted(UUID.randomUUID())))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/guilds/{guildId}/invites", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "maxAgeSeconds": 0,
                      "maxUses": 0,
                      "temporary": false,
                      "roleGrantIds": ["%s"]
                    }
                    """.formatted(channelId, UUID.randomUUID())))
            .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsInviteCreateWhenRequesterCannotGrantRole() throws Exception {
        AuthSession owner = signup("invite_owner_grant_ceiling");
        AuthSession delegate = signup("invite_delegate_grant_ceiling");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, owner);
        String managerRoleId = createRole(guildId, "invite_channel_manager", owner, "MANAGE_CHANNELS");
        String adminRoleId = createRole(guildId, "invite_admin_grant", owner, "ADMINISTRATOR");
        addMember(guildId, delegate.userId(), owner);
        assignRole(guildId, delegate.userId(), managerRoleId, owner);

        mockMvc.perform(post("/api/guilds/{guildId}/invites", guildId)
                .header("Authorization", delegate.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "maxAgeSeconds": 0,
                      "maxUses": 0,
                      "temporary": false,
                      "roleGrantIds": ["%s"]
                    }
                    """.formatted(channelId, adminRoleId)))
            .andExpect(status().isForbidden());
    }

    @Test
    void deletesExpiredInviteIdempotently() throws Exception {
        AuthSession owner = signup("invite_owner_delete_expired");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, owner);
        String code = createInvite(guildId, channelId, List.of(), owner, -1, 0);

        mockMvc.perform(delete("/api/invites/{code}", code)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/invites/{code}", code)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNoContent());
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

    private String createChannel(String guildId, AuthSession requester) throws Exception {
        MvcResult channelResult = mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "general",
                      "type": "GUILD_TEXT",
                      "parentId": null
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(channelResult.getResponse().getContentAsString(), "$.id");
    }

    private String createRole(String guildId, String name, AuthSession requester, String... permissions) throws Exception {
        String permissionJson = permissions.length == 0 ? "[]" : "[\"" + String.join("\",\"", permissions) + "\"]";
        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "%s",
                      "permissions": %s
                    }
                    """.formatted(name, permissionJson)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id");
    }

    private String createInvite(
        String guildId,
        String channelId,
        List<String> roleIds,
        AuthSession requester,
        long maxAgeSeconds,
        int maxUses
    )
        throws Exception {
        String roleGrantIds = roleIds.isEmpty() ? "[]" : "[\"" + String.join("\",\"", roleIds) + "\"]";
        MvcResult inviteResult = mockMvc.perform(post("/api/guilds/{guildId}/invites", guildId)
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "maxAgeSeconds": %d,
                      "maxUses": %d,
                      "temporary": false,
                      "roleGrantIds": %s
                    }
                    """.formatted(channelId, maxAgeSeconds, maxUses, roleGrantIds)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.guildId").value(guildId))
            .andReturn();

        return JsonPath.read(inviteResult.getResponse().getContentAsString(), "$.code");
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

    private void addMember(String guildId, UUID memberId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, memberId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());
    }

    private void assignRole(String guildId, UUID memberId, String roleId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, memberId, roleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());
    }

    private record AuthSession(String accessToken, UUID userId) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
