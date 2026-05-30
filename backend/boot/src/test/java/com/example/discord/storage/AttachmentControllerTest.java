package com.example.discord.storage;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class AttachmentControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void createsPresignedUploadForAuthenticatedChannelMember() throws Exception {
        AuthSession owner = signup("attachment_upload_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);

        mockMvc.perform(post("/api/attachments/uploads")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "filename": "../../secret.png",
                      "contentType": "image/png",
                      "sizeBytes": 512
                    }
                    """.formatted(channelId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.attachmentId").isNotEmpty())
            .andExpect(jsonPath("$.objectKey").value(containsString("/" + channelId + "/" + owner.userId() + "/")))
            .andExpect(jsonPath("$.objectKey").value(not(containsString("secret"))))
            .andExpect(jsonPath("$.uploadUrl").value(containsString("memory://upload/attachments/" + guildId)));
    }

    @Test
    void rejectsInvalidUploadContentType() throws Exception {
        AuthSession owner = signup("attachment_invalid_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);

        mockMvc.perform(post("/api/attachments/uploads")
                .header("Authorization", owner.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "filename": "notes.txt",
                      "contentType": "text/plain",
                      "sizeBytes": 10
                    }
                    """.formatted(channelId)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("invalid attachment"));
    }

    @Test
    void downloadUrlIsScopedToAttachmentOwnerAndChannel() throws Exception {
        AuthSession owner = signup("attachment_download_owner");
        AuthSession other = signup("attachment_download_other");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        addMember(guildId, other.userId(), owner);
        String attachmentId = requestUpload(channelId, "image.png", "image/png", owner);
        markUploaded(attachmentId, owner);
        String messageId = createMessage(channelId, "with attachment", owner);
        attachToMessage(channelId, messageId, attachmentId, owner);

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.attachmentId").value(attachmentId))
            .andExpect(jsonPath("$.downloadUrl").value(containsString("memory://download/attachments/" + guildId)));

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                .header("Authorization", other.bearer()))
            .andExpect(status().isNotFound());
    }

    @Test
    void cleanupDeletesUploadedOrphans() throws Exception {
        AuthSession owner = signup("attachment_cleanup_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        String attachmentId = requestUpload(channelId, "orphan.png", "image/png", owner);
        markUploaded(attachmentId, owner);
        Thread.sleep(5);

        mockMvc.perform(delete("/api/attachments/orphans")
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(1));

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", attachmentId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNotFound());
    }

    @Test
    void cleanupOnlyDeletesRequestersOwnOrphans() throws Exception {
        AuthSession owner = signup("attachment_cleanup_scoped_owner");
        AuthSession other = signup("attachment_cleanup_scoped_other");
        String ownerGuildId = createGuild(owner);
        String ownerChannelId = createChannel(ownerGuildId, "general", owner);
        String otherGuildId = createGuild(other);
        String otherChannelId = createChannel(otherGuildId, "general", other);
        String ownerAttachmentId = requestUpload(ownerChannelId, "owner-orphan.png", "image/png", owner);
        String otherAttachmentId = requestUpload(otherChannelId, "other-orphan.png", "image/png", other);
        markUploaded(ownerAttachmentId, owner);
        markUploaded(otherAttachmentId, other);
        Thread.sleep(5);

        mockMvc.perform(delete("/api/attachments/orphans")
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(1));

        mockMvc.perform(get("/api/attachments/{attachmentId}/download", ownerAttachmentId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/attachments/orphans")
                .header("Authorization", other.bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deletedCount").value(1));
    }

    private String requestUpload(String channelId, String filename, String contentType, AuthSession requester) throws Exception {
        MvcResult upload = mockMvc.perform(post("/api/attachments/uploads")
                .header("Authorization", requester.bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "channelId": "%s",
                      "filename": "%s",
                      "contentType": "%s",
                      "sizeBytes": 512
                    }
                    """.formatted(channelId, filename, contentType)))
            .andExpect(status().isCreated())
            .andReturn();
        return JsonPath.read(upload.getResponse().getContentAsString(), "$.attachmentId");
    }

    private void markUploaded(String attachmentId, AuthSession requester) throws Exception {
        mockMvc.perform(put("/api/attachments/{attachmentId}/uploaded", attachmentId)
                .header("Authorization", requester.bearer()))
            .andExpect(status().isOk());
    }

    private void attachToMessage(String channelId, String messageId, String attachmentId, AuthSession requester) throws Exception {
        mockMvc.perform(post(
                "/api/channels/{channelId}/messages/{messageId}/attachments/{attachmentId}",
                channelId,
                messageId,
                attachmentId
            )
                .header("Authorization", requester.bearer()))
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
