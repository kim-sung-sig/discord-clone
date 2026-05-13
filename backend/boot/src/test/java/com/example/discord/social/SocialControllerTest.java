package com.example.discord.social;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class SocialControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void acceptsFriendRequestByAddressee() throws Exception {
        AuthSession alice = signup("social_friend_alice");
        AuthSession bob = signup("social_friend_bob");
        String requestId = createFriendRequest(alice, bob.userId());

        mockMvc.perform(put("/api/social/friends/requests/{requestId}/accept", requestId)
                .header("Authorization", bob.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(requestId))
            .andExpect(jsonPath("$.requesterId").value(alice.userId().toString()))
            .andExpect(jsonPath("$.addresseeId").value(bob.userId().toString()))
            .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void blockPreventsDirectMessageSend() throws Exception {
        AuthSession alice = signup("social_block_alice");
        AuthSession bob = signup("social_block_bob");

        mockMvc.perform(put("/api/social/blocks/{targetUserId}", bob.userId())
                .header("Authorization", alice.bearer()))
            .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/social/dms/{targetUserId}/messages", alice.userId())
                .header("Authorization", bob.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "content": "blocked hello"
                    }
                    """))
            .andExpect(status().isForbidden());
    }

    @Test
    void authorizesOnlyGroupOwnerToAddAndRemoveMembers() throws Exception {
        AuthSession owner = signup("social_group_owner");
        AuthSession member = signup("social_group_member");
        AuthSession added = signup("social_group_added");
        AuthSession outsider = signup("social_group_outsider");
        String groupId = createGroup(owner, member.userId());

        mockMvc.perform(put("/api/social/group-dms/{groupId}/members/{memberId}", groupId, outsider.userId())
                .header("Authorization", member.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/social/group-dms/{groupId}/members/{memberId}", groupId, added.userId())
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[?(@ == '%s')]".formatted(added.userId())).exists());

        mockMvc.perform(delete("/api/social/group-dms/{groupId}/members/{memberId}", groupId, added.userId())
                .header("Authorization", outsider.bearer()))
            .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/social/group-dms/{groupId}/members/{memberId}", groupId, added.userId())
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.members[?(@ == '%s')]".formatted(added.userId())).doesNotExist());
    }

    private String createFriendRequest(AuthSession requester, UUID targetUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/social/friends/requests")
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "targetUserId": "%s"
                    }
                    """.formatted(targetUserId)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
    }

    private String createGroup(AuthSession owner, UUID memberId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/social/group-dms")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Launch Crew",
                      "memberIds": ["%s"]
                    }
                    """.formatted(memberId)))
            .andExpect(status().isCreated())
            .andReturn();

        return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
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
