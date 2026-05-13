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
        UUID ownerId = UUID.randomUUID();

        MvcResult guildResult = mockMvc.perform(post("/api/guilds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Discord Clone",
                      "ownerId": "%s"
                    }
                    """.formatted(ownerId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Discord Clone"))
            .andExpect(jsonPath("$.ownerId").value(ownerId.toString()))
            .andReturn();

        String guildId = JsonPath.read(guildResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
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
                .param("memberId", ownerId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("general"));
    }

    @Test
    void rejectsChannelCreateWithoutTypeAsBadRequest() throws Exception {
        UUID ownerId = UUID.randomUUID();

        MvcResult guildResult = mockMvc.perform(post("/api/guilds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Discord Clone",
                      "ownerId": "%s"
                    }
                    """.formatted(ownerId)))
            .andExpect(status().isCreated())
            .andReturn();

        String guildId = JsonPath.read(guildResult.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
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
        UUID ownerId = UUID.randomUUID();
        String guildId = createGuild(ownerId);
        String staffChannelId = createChannel(guildId, "staff");

        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
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

        mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].name").value("@everyone"))
            .andExpect(jsonPath("$[1].id").value(roleId))
            .andExpect(jsonPath("$[1].permissions[0]").value("SEND_MESSAGES"));

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "permissions": ["MANAGE_CHANNELS"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(roleId))
            .andExpect(jsonPath("$.permissions[0]").value("MANAGE_CHANNELS"));

        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}/roles/{roleId}", guildId, ownerId, roleId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.memberId").value(ownerId.toString()))
            .andExpect(jsonPath("$.roleIds").isArray())
            .andExpect(jsonPath("$.roleIds", Matchers.hasItem(roleId)));

        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                staffChannelId,
                roleId
            )
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
                .param("memberId", ownerId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsNullPermissionListAsBadRequest() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String guildId = createGuild(ownerId);

        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
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
        UUID ownerId = UUID.randomUUID();
        String guildId = createGuild(ownerId);
        String roleId = createRole(guildId, "moderator");

        mockMvc.perform(put("/api/guilds/{guildId}/roles/{roleId}/permissions", guildId, roleId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("null"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid request"));
    }

    @Test
    void rejectsNullOverwritePermissionListsAsBadRequest() throws Exception {
        UUID ownerId = UUID.randomUUID();
        String guildId = createGuild(ownerId);
        String channelId = createChannel(guildId, "staff");
        String roleId = createRole(guildId, "moderator");

        mockMvc.perform(put(
                "/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId,
                channelId,
                roleId
            )
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

    private String createGuild(UUID ownerId) throws Exception {
        MvcResult guildResult = mockMvc.perform(post("/api/guilds")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Discord Clone",
                      "ownerId": "%s"
                    }
                    """.formatted(ownerId)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(guildResult.getResponse().getContentAsString(), "$.id");
    }

    private String createChannel(String guildId, String name) throws Exception {
        MvcResult channelResult = mockMvc.perform(post("/api/guilds/{guildId}/channels", guildId)
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

    private String createRole(String guildId, String name) throws Exception {
        MvcResult roleResult = mockMvc.perform(post("/api/guilds/{guildId}/roles", guildId)
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
}
