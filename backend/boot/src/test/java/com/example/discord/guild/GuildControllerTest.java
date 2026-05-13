package com.example.discord.guild;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
