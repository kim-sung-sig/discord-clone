package com.example.discord.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"postgres", "legacy-auth"})
@EnabledIfEnvironmentVariable(named = "DISCORD_RUN_POSTGRES_TESTS", matches = "true")
class PostgresNotificationFlowControllerTest {
    @Autowired private MockMvc mvc;

    @Test
    void serverEventAndDmRemainReadableThroughAuthenticatedInbox() throws Exception {
        Session owner = signup("t43_flow_owner");
        Session member = signup("t43_flow_member");
        Session outsider = signup("t43_flow_outsider");
        String guildId = JsonPath.read(mvc.perform(post("/api/guilds")
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"T43 Flow\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String channelId = JsonPath.read(mvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"general\",\"type\":\"GUILD_TEXT\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, member.userId())
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());

        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/presence/channels/{channelId}/read", channelId))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/guilds/{guildId}/events", guildId)
                .header("Authorization", outsider.bearer()))
            .andExpect(status().isForbidden());
        mvc.perform(post("/api/guilds/{guildId}/events", guildId)
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(eventRequest(channelId).replace("Meetup", "")))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/guilds/{guildId}/events", guildId)
                .header("Authorization", member.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(eventRequest(channelId)))
            .andExpect(status().isForbidden());
        String eventId = JsonPath.read(mvc.perform(post("/api/guilds/{guildId}/events", guildId)
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(eventRequest(channelId)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        MvcResult inbox = mvc.perform(get("/api/notifications").header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].kind").value("SERVER"))
            .andExpect(jsonPath("$[0].eventId").value(eventId)).andReturn();
        String itemId = JsonPath.read(inbox.getResponse().getContentAsString(), "$[0].id");
        mvc.perform(put("/api/notifications/{itemId}/read", itemId).header("Authorization", member.bearer()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(post("/api/channels/{channelId}/messages", channelId)
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"channel unread\",\"idempotencyKey\":\"t43-channel-unread\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications/channel-unread").header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$['" + channelId + "']").value(1));
        mvc.perform(get("/api/presence/channels/{channelId}/read", channelId)
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lastReadSequence").value(0));

        String hiddenChannel = JsonPath.read(mvc.perform(post("/api/guilds/{guildId}/channels", guildId)
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"hidden\",\"type\":\"GUILD_TEXT\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String roles = mvc.perform(get("/api/guilds/{guildId}/roles", guildId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String everyoneRoleId = JsonPath.read(roles, "$[0].id");
        mvc.perform(put("/api/guilds/{guildId}/channels/{channelId}/overwrites/roles/{roleId}",
                guildId, hiddenChannel, everyoneRoleId)
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"allow\":[],\"deny\":[\"VIEW_CHANNEL\"]}"))
            .andExpect(status().isOk());
        mvc.perform(post("/api/guilds/{guildId}/events", guildId)
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content(eventRequest(hiddenChannel)))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(get("/api/presence/channels/{channelId}/read", hiddenChannel)
                .header("Authorization", member.bearer()))
            .andExpect(status().isForbidden());

        mvc.perform(post("/api/social/dms/{targetUserId}/messages", member.userId())
                .header("Authorization", owner.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"persistent hello\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/social/dms/{targetUserId}/messages", owner.userId())
                .header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].content").value("persistent hello"));
        mvc.perform(get("/api/social/dms/{targetUserId}/messages", owner.userId()))
            .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/notifications").header("Authorization", member.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].kind").value("DM"))
            .andExpect(jsonPath("$[0].targetUserId").value(owner.userId().toString()));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
        mvc.perform(put("/api/social/dms/{targetUserId}/read", owner.userId())
                .header("Authorization", member.bearer()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"lastReadSequence\":1}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.lastReadSequence").value(1));
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(0));
        mvc.perform(put("/api/social/blocks/{userId}", owner.userId()).header("Authorization", member.bearer()))
            .andExpect(status().isNoContent());
        for (Session[] pair : new Session[][] {{owner, member}, {member, owner}}) {
            mvc.perform(get("/api/social/dms/{targetUserId}/messages", pair[1].userId())
                    .header("Authorization", pair[0].bearer()))
                .andExpect(status().isForbidden());
            mvc.perform(get("/api/social/dms/{targetUserId}/read", pair[1].userId())
                    .header("Authorization", pair[0].bearer()))
                .andExpect(status().isForbidden());
            mvc.perform(put("/api/social/dms/{targetUserId}/read", pair[1].userId())
                    .header("Authorization", pair[0].bearer()).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"lastReadSequence\":1}"))
                .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/notifications").header("Authorization", member.bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.kind == 'DM')]").isEmpty());
    }

    private static String eventRequest(String channelId) {
        return """
            {"channelId":"%s","title":"Meetup","startsAt":"2026-10-01T10:00:00Z","endsAt":"2026-10-01T11:00:00Z"}
            """.formatted(channelId);
    }

    private Session signup(String username) throws Exception {
        username = username + "_" + UUID.randomUUID().toString().substring(0, 8);
        String uniqueUsername = username;
        MvcResult result = mvc.perform(post("/api/auth/signup")
                .with(request -> { request.setRemoteAddr("192.0.2." + Math.abs(uniqueUsername.hashCode() % 200 + 1)); return request; })
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"email":"%s@example.com","username":"%s","displayName":"%s","password":"correct horse battery staple"}
                    """.formatted(username, username, username)))
            .andExpect(status().isCreated()).andReturn();
        String body = result.getResponse().getContentAsString();
        return new Session(JsonPath.read(body, "$.accessToken"), UUID.fromString(JsonPath.read(body, "$.user.id")));
    }

    private record Session(String token, UUID userId) {
        String bearer() { return "Bearer " + token; }
    }
}
