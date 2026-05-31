package com.example.discord.guild;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GuildControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsGuildAndChannelThenListsVisibleChannels() throws Exception {
        AuthSession owner = signup("guild_owner");
        String guildId = createGuild(owner);

        mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "general",
                      "type": "GUILD_TEXT",
                      "parentId": null
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("general"))
            .andExpect(jsonPath("$.type").value("GUILD_TEXT"));

        mockMvc.perform(get("/api/guilds/{guildId}/channels/visible", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("general"));
    }

    @Test
    void rejectsVisibleChannelsWithoutBearerToken() throws Exception {
        AuthSession owner = signup("vis_missing_owner");
        String guildId = createGuild(owner);

        mockMvc.perform(get("/api/guilds/{guildId}/channels/visible", guildId)
                .param("memberId", owner.userId().toString()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void visibleChannelsUsesAuthenticatedUserInsteadOfQueryMemberId() throws Exception {
        AuthSession owner = signup("visible_channels_owner_subject");
        AuthSession member = signup("visible_channels_member_subject");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "staff", owner);
        addMember(guildId, member.userId(), owner);
        String everyoneRoleId = firstRoleId(guildId, owner);

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

        mockMvc.perform(get("/api/guilds/{guildId}/channels/visible", guildId)
                .header("Authorization", member.bearer())
                .param("memberId", owner.userId().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void listsAuthenticatedUsersGuildsWithVisibleChannelsForShellBootstrap() throws Exception {
        AuthSession owner = signup("shell_owner");
        AuthSession member = signup("shell_member");
        AuthSession outsider = signup("shell_outsider");
        String ownedGuildId = createGuild(owner);
        String memberGuildId = createGuild(member);
        String hiddenChannelId = createChannel(memberGuildId, "staff", member);
        String everyoneRoleId = firstRoleId(memberGuildId, member);

        addMember(memberGuildId, owner.userId(), member);
        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                memberGuildId,
                hiddenChannelId,
                everyoneRoleId
            )
                .header("Authorization", member.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allow": [],
                      "deny": ["VIEW_CHANNEL"]
                    }
                    """))
            .andExpect(status().isOk());
        createGuild(outsider);
        createChannel(ownedGuildId, "general", owner);

        mockMvc.perform(get("/api/users/@me/guilds")
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.guilds", Matchers.hasSize(2)))
            .andExpect(jsonPath("$.guilds[0].id").value(ownedGuildId))
            .andExpect(jsonPath("$.guilds[0].ownerId").value(owner.userId().toString()))
            .andExpect(jsonPath("$.guilds[0].channels[0].name").value("general"))
            .andExpect(jsonPath("$.guilds[0].channels[0].type").value("GUILD_TEXT"))
            .andExpect(jsonPath("$.guilds[1].id").value(memberGuildId))
            .andExpect(jsonPath("$.guilds[1].channels").isEmpty());
    }

    @Test
    void rejectsAuthenticatedGuildShellBootstrapWithoutBearerToken() throws Exception {
        mockMvc.perform(get("/api/users/@me/guilds"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsChannelCreateWithoutTypeAsBadRequest() throws Exception {
        AuthSession owner = signup("invalid_channel_owner");
        String guildId = createGuild(owner);

        mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "general",
                      "parentId": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));
    }

    @Test
    void managesRolesPermissionsAssignmentsAndChannelRoleOverwrites() throws Exception {
        AuthSession owner = signup("role_owner");
        String guildId = createGuild(owner);
        String staffChannelId = createChannel(guildId, "staff", owner);

        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "moderator",
                      "permissions": ["SEND_MESSAGES"]
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("moderator"))
            .andExpect(jsonPath("$.permissions[0]").value("SEND_MESSAGES"))
            .andReturn();

        String roleId = JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("@everyone"))
            .andExpect(jsonPath("$[1].id").value(roleId))
            .andExpect(jsonPath("$[1].permissions[0]").value("SEND_MESSAGES"));

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, roleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["MANAGE_CHANNELS"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(roleId))
            .andExpect(jsonPath("$.permissions[0]").value("MANAGE_CHANNELS"));

        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, owner.userId(), roleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId").value(owner.userId().toString()))
            .andExpect(jsonPath("$.roleIds").isArray())
            .andExpect(jsonPath("$.roleIds", Matchers.hasItem(roleId)));

        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                staffChannelId,
                roleId
            )
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allow": [],
                      "deny": ["VIEW_CHANNEL"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(staffChannelId));

        mockMvc.perform(get("/api/guilds/{guildId}/channels/visible", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsRoleListWithoutBearerToken() throws Exception {
        AuthSession owner = signup("roles_missing_token_owner");
        String guildId = createGuild(owner);

        mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRoleListForNonMember() throws Exception {
        AuthSession owner = signup("roles_owner_subject");
        AuthSession outsider = signup("roles_outsider_subject");
        String guildId = createGuild(owner);

        mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", outsider.bearer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsNullPermissionListAsBadRequest() throws Exception {
        AuthSession owner = signup("null_permission_owner");
        String guildId = createGuild(owner);

        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "moderator",
                      "permissions": []
                    }
                    """))
            .andExpect(status().isCreated())
            .andReturn();

        String roleId = JsonPath.read(roleResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, roleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));

    }

    @Test
    void rejectsNullRolePermissionBodyAsBadRequest() throws Exception {
        AuthSession owner = signup("null_body_owner");
        String guildId = createGuild(owner);
        String roleId = createRole(guildId, "moderator", owner);

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, roleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));
    }

    @Test
    void rejectsNullOverwritePermissionListsAsBadRequest() throws Exception {
        AuthSession owner = signup("null_overwrite_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "staff", owner);
        String roleId = createRole(guildId, "moderator", owner);

        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                channelId,
                roleId
            )
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allow": null,
                      "deny": ["VIEW_CHANNEL"]
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));

        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                channelId,
                roleId
            )
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allow": ["VIEW_CHANNEL"],
                      "deny": null
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));
    }

    @Test
    void rejectsGuildCreateWithoutBearerToken() throws Exception {
        mockMvc.perform(post("/api/guilds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Discord Clone"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsGuildCreateWithInvalidBearerToken() throws Exception {
        mockMvc.perform(post("/api/guilds")
                .header("Authorization", "Bearer not-a-valid-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Discord Clone"
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRoleMutationWithoutBearerToken() throws Exception {
        AuthSession owner = signup("owner_missing_mutation_token");
        String guildId = createGuild(owner);

        mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "moderator",
                      "permissions": []
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsMalformedGuildMutationWithoutBearerBeforeBodyValidation() throws Exception {
        AuthSession owner = signup("owner_malformed_unauth");
        String guildId = createGuild(owner);

        mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsEveryMutationTypeWithoutBearerToken() throws Exception {
        AuthSession owner = signup("owner_missing_matrix_token");
        AuthSession member = signup("member_missing_matrix_token");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "staff", owner);
        String roleId = createRole(guildId, "moderator", owner);

        mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "missing-token",
                      "type": "GUILD_TEXT",
                      "parentId": null
                    }
                    """))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["MANAGE_ROLES"]
                    }
                    """))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, member.userId()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, member.userId(), roleId))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                channelId,
                roleId
            )
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allow": [],
                      "deny": ["VIEW_CHANNEL"]
                    }
                    """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsRoleMutationFromNonOwnerWithoutManageRoles() throws Exception {
        AuthSession owner = signup("owner_no_manage_roles");
        AuthSession outsider = signup("outsider_no_manage_roles");
        String guildId = createGuild(owner);

        mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", outsider.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "moderator",
                      "permissions": []
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMemberWithManageRolesToMutateRoles() throws Exception {
        AuthSession owner = signup("owner_manage_roles");
        AuthSession delegate = signup("delegate_manage_roles");
        String guildId = createGuild(owner);
        String managerRoleId = createRole(guildId, "role_manager", owner);

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, managerRoleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["MANAGE_ROLES"]
                    }
                    """))
            .andExpect(status().isOk());

        addMember(guildId, delegate.userId(), owner);

        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, delegate.userId(), managerRoleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", delegate.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "helper",
                      "permissions": []
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("helper"));
    }

    @Test
    void rejectsManageRolesDelegateGrantingAdministrator() throws Exception {
        AuthSession owner = signup("owner_admin_escalation");
        AuthSession delegate = signup("delegate_admin_escalation");
        String guildId = createGuild(owner);
        String managerRoleId = createRole(guildId, "role_manager", owner);

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, managerRoleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["MANAGE_ROLES"]
                    }
                    """))
            .andExpect(status().isOk());

        addMember(guildId, delegate.userId(), owner);
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, delegate.userId(), managerRoleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", delegate.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "admin",
                      "permissions": ["ADMINISTRATOR"]
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsManageRolesDelegateAssigningHigherRole() throws Exception {
        AuthSession owner = signup("owner_assign_escalation");
        AuthSession delegate = signup("delegate_assign_escalation");
        String guildId = createGuild(owner);
        String managerRoleId = createRole(guildId, "role_manager", owner);
        String adminRoleId = createRole(guildId, "admin", owner);

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, managerRoleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["MANAGE_ROLES"]
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, adminRoleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["ADMINISTRATOR"]
                    }
                    """))
            .andExpect(status().isOk());

        addMember(guildId, delegate.userId(), owner);
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, delegate.userId(), managerRoleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, delegate.userId(), adminRoleId)
                .header("Authorization", delegate.bearer()))
            .andExpect(status().isForbidden());
    }

    @Test
    void allowsMemberWithManageChannelsToMutateChannels() throws Exception {
        AuthSession owner = signup("owner_manage_channels");
        AuthSession delegate = signup("delegate_manage_channels");
        String guildId = createGuild(owner);
        String managerRoleId = createRole(guildId, "channel_manager", owner);

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, managerRoleId)
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["MANAGE_CHANNELS"]
                    }
                    """))
            .andExpect(status().isOk());

        addMember(guildId, delegate.userId(), owner);

        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, delegate.userId(), managerRoleId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", delegate.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "managed",
                      "type": "GUILD_TEXT",
                      "parentId": null
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("managed"));

        String roleId = createRole(guildId, "viewer", owner);
        String channelId = createChannel(guildId, "overwritten", owner);

        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                channelId,
                roleId
            )
                .header("Authorization", delegate.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "allow": [],
                      "deny": ["VIEW_CHANNEL"]
                    }
                    """))
            .andExpect(status().isOk());
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
            .andExpect(jsonPath("$.ownerId").value(owner.userId().toString()))
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

    private String firstRoleId(String guildId, AuthSession requester) throws Exception {
        MvcResult roleResult = mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", requester.bearer()))
            .andExpect(status().isOk())
            .andReturn();

        return JsonPath.read(roleResult.getResponse().getContentAsString(), "$[0].id");
    }

    private AuthSession signup(String username) throws Exception {
        MvcResult signup = mockMvc.perform(post("/api/auth/signup")
                .with(request -> {
                    request.setRemoteAddr(testClientIp(username));
                    return request;
                })
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

    private void addMember(String guildId, UUID memberId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, memberId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());
    }

    private record AuthSession(String accessToken, UUID userId) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }
}
