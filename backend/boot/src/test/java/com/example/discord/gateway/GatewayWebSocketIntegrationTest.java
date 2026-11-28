package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "discord.gateway.internal-publisher-token=test-harness"
)
@AutoConfigureMockMvc
class GatewayWebSocketIntegrationTest extends GatewayWebSocketTestSupport {
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void websocketIdentifyReceivesReadyAndPublishedEvents() throws Exception {
        AuthSession owner = signup("gateway_ws_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        SocketProbe probe = connect();

        probe.session().sendMessage(new TextMessage("""
            {
              "type": "IDENTIFY",
              "accessToken": "%s"
            }
            """.formatted(owner.accessToken())));

        String ready = probe.nextMessage();
        assertThat(JsonPath.<String>read(ready, "$.type")).isEqualTo("READY");
        assertThat(JsonPath.<String>read(ready, "$.userId")).isEqualTo(owner.userId().toString());

        publish(owner, "MESSAGE_CREATE", guildId, channelId, "hello websocket");

        String event = probe.nextMessage();
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("EVENT");
        assertThat(JsonPath.<String>read(event, "$.eventType")).isEqualTo("MESSAGE_CREATE");
        assertThat(JsonPath.<String>read(event, "$.payload.content")).isEqualTo("hello websocket");
    }

    @Test
    void websocketDoesNotDeliverHiddenChannelEvents() throws Exception {
        AuthSession owner = signup("gateway_ws_hidden_owner");
        AuthSession member = signup("gateway_ws_hidden_member");
        String guildId = createGuild(owner);
        String visibleChannelId = createChannel(guildId, "general", owner);
        String hiddenChannelId = createChannel(guildId, "staff", owner);
        addMember(guildId, member.userId(), owner);
        denyEveryoneView(guildId, hiddenChannelId, owner);
        SocketProbe probe = connect();

        probe.session().sendMessage(new TextMessage("""
            {
              "type": "IDENTIFY",
              "accessToken": "%s"
            }
            """.formatted(member.accessToken())));
        assertThat(JsonPath.<String>read(probe.nextMessage(), "$.type")).isEqualTo("READY");

        publish(owner, "MESSAGE_CREATE", guildId, hiddenChannelId, "hidden websocket");
        publish(owner, "MESSAGE_CREATE", guildId, visibleChannelId, "visible websocket");

        String event = probe.nextMessage();
        assertThat(JsonPath.<String>read(event, "$.type")).isEqualTo("EVENT");
        assertThat(JsonPath.<String>read(event, "$.payload.content")).isEqualTo("visible websocket");
    }

    @Test
    void websocketHeartbeatAcknowledgesIdentifiedSession() throws Exception {
        AuthSession owner = signup("gateway_ws_heartbeat_owner");
        createGuild(owner);
        SocketProbe probe = connect();

        probe.session().sendMessage(new TextMessage("""
            {
              "type": "IDENTIFY",
              "accessToken": "%s"
            }
            """.formatted(owner.accessToken())));
        String ready = probe.nextMessage();
        Number readySequence = JsonPath.read(ready, "$.sequence");

        probe.session().sendMessage(new TextMessage("""
            {
              "type": "HEARTBEAT",
              "lastSequence": %d
            }
            """.formatted(readySequence.longValue())));

        String ack = probe.nextMessage();
        assertThat(JsonPath.<String>read(ack, "$.type")).isEqualTo("HEARTBEAT_ACK");
        assertThat(JsonPath.<Number>read(ack, "$.sequence").longValue()).isGreaterThan(readySequence.longValue());
    }

    @Test
    void websocketResumeReplaysMissedAuthorizedEvents() throws Exception {
        AuthSession owner = signup("gateway_ws_resume_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        SocketProbe first = connect();

        first.session().sendMessage(new TextMessage("""
            {
              "type": "IDENTIFY",
              "accessToken": "%s"
            }
            """.formatted(owner.accessToken())));
        String ready = first.nextMessage();
        String gatewaySessionId = JsonPath.read(ready, "$.sessionId");
        Number readySequence = JsonPath.read(ready, "$.sequence");
        first.session().close();

        publish(owner, "MESSAGE_CREATE", guildId, channelId, "missed websocket");

        SocketProbe second = connect();
        second.session().sendMessage(new TextMessage("""
            {
              "type": "RESUME",
              "sessionId": "%s",
              "accessToken": "%s",
              "lastSequence": %d
            }
            """.formatted(gatewaySessionId, owner.accessToken(), readySequence.longValue())));

        String resumed = second.nextMessage();
        assertThat(JsonPath.<String>read(resumed, "$.type")).isEqualTo("RESUMED");
        String replayed = second.nextMessage();
        assertThat(JsonPath.<String>read(replayed, "$.type")).isEqualTo("EVENT");
        assertThat(JsonPath.<String>read(replayed, "$.payload.content")).isEqualTo("missed websocket");
    }

    @Test
    void clientFrameContractFixtureIsAcceptedByTheServer() throws Exception {
        JsonNode frames = objectMapper.readTree(Files.readString(clientFrameFixture()));
        AuthSession owner = signup("gateway_ws_contract_owner");
        String guildId = createGuild(owner);
        String channelId = createChannel(guildId, "general", owner);
        // Seed the event log so resuming past control frames does not trigger the server's empty-log RESYNC_REQUIRED.
        publish(owner, "MESSAGE_CREATE", guildId, channelId, "before identify");
        SocketProbe first = connect();

        first.session().sendMessage(new TextMessage(frame(frames.get("identify"), Map.of("accessToken", owner.accessToken()))));
        String ready = first.nextMessage();
        assertThat(JsonPath.<String>read(ready, "$.type")).isEqualTo("READY");
        String gatewaySessionId = JsonPath.read(ready, "$.sessionId");
        long readySequence = JsonPath.<Number>read(ready, "$.sequence").longValue();

        first.session().sendMessage(new TextMessage(frame(frames.get("heartbeat"), Map.of("lastSequence", readySequence))));
        assertThat(JsonPath.<String>read(first.nextMessage(), "$.type")).isEqualTo("HEARTBEAT_ACK");
        first.session().close();

        SocketProbe second = connect();
        second.session().sendMessage(new TextMessage(frame(frames.get("resume"), Map.of(
            "accessToken", owner.accessToken(),
            "sessionId", gatewaySessionId,
            "lastSequence", readySequence
        ))));
        assertThat(JsonPath.<String>read(second.nextMessage(), "$.type")).isEqualTo("RESUMED");
    }

    @Test
    void legacyOpTokenFrameIsRejectedWithUnknownOpcodeHint() throws Exception {
        SocketProbe probe = connect();

        probe.session().sendMessage(new TextMessage("""
            {
              "op": "IDENTIFY",
              "token": "ignored"
            }
            """));

        String error = probe.nextMessage();
        assertThat(JsonPath.<String>read(error, "$.type")).isEqualTo("ERROR");
        assertThat(JsonPath.<String>read(error, "$.code")).isEqualTo("UNKNOWN_OPCODE");
        assertThat(JsonPath.<String>read(error, "$.message")).contains("\"type\"");
    }

    private String frame(JsonNode fixtureFrame, Map<String, Object> values) {
        Set<String> fixtureFields = new HashSet<>();
        fixtureFrame.fieldNames().forEachRemaining(fixtureFields::add);
        fixtureFields.remove("type");
        assertThat(values.keySet())
            .as("every fixture field besides type must be filled by the test, and no field may be missing from the fixture")
            .isEqualTo(fixtureFields);
        ObjectNode copy = fixtureFrame.deepCopy();
        values.forEach((name, value) -> copy.set(name, objectMapper.valueToTree(value)));
        return copy.toString();
    }

    private static Path clientFrameFixture() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("qa/contracts/gateway-client-frames.json");
            if (Files.exists(candidate)) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("qa/contracts/gateway-client-frames.json not found above " + Path.of("").toAbsolutePath());
    }

    private long publish(AuthSession requester, String type, String guildId, String channelId, String content) throws Exception {
        String channelJson = channelId == null ? "null" : "\"" + channelId + "\"";
        MvcResult result = mockMvc.perform(post("/api/gateway/events")
                .header("Authorization", requester.bearer())
                .header("X-Internal-Gateway-Publisher", "test-harness")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "type": "%s",
                      "guildId": "%s",
                      "channelId": %s,
                      "payload": {
                        "content": "%s"
                      }
                    }
                    """.formatted(type, guildId, channelJson, content)))
            .andExpect(status().isCreated())
            .andReturn();

        Number sequence = JsonPath.read(result.getResponse().getContentAsString(), "$.sequence");
        return sequence.longValue();
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

    private void addMember(String guildId, UUID memberId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, memberId)
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
}
