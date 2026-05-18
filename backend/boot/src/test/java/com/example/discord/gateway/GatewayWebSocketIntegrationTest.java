package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "discord.gateway.internal-publisher-token=test-harness",
        "discord.gateway.heartbeat-timeout-ms=100"
    }
)
@AutoConfigureMockMvc
class GatewayWebSocketIntegrationTest {
    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GatewaySessionMaintenance sessionMaintenance;

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
    void websocketHeartbeatTimeoutClosesStaleSocket() throws Exception {
        AuthSession owner = signup("gateway_ws_timeout_owner");
        createGuild(owner);
        SocketProbe probe = connect();

        probe.session().sendMessage(new TextMessage("""
            {
              "type": "IDENTIFY",
              "accessToken": "%s"
            }
            """.formatted(owner.accessToken())));
        assertThat(JsonPath.<String>read(probe.nextMessage(), "$.type")).isEqualTo("READY");

        Thread.sleep(150);
        sessionMaintenance.closeTimedOutSessions();

        assertThat(probe.awaitClose()).isTrue();
    }

    private SocketProbe connect() throws Exception {
        BlockingQueue<String> messages = new LinkedBlockingQueue<>();
        BlockingQueue<String> closeStatuses = new LinkedBlockingQueue<>();
        WebSocketSession session = new StandardWebSocketClient()
            .execute(new TextWebSocketHandler() {
                @Override
                protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                    messages.add(message.getPayload());
                }

                @Override
                public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
                    closeStatuses.add(status.toString());
                }
            }, "ws://127.0.0.1:" + port + "/ws/gateway")
            .get(5, TimeUnit.SECONDS);
        return new SocketProbe(session, messages, closeStatuses);
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

    private void addMember(String guildId, UUID memberId, AuthSession owner) throws Exception {
        mockMvc.perform(put("/api/guilds/{guildId}/members/{memberId}", guildId, memberId)
                .header("Authorization", owner.bearer()))
            .andExpect(status().isOk());
    }

    private void denyEveryoneView(String guildId, String channelId, AuthSession owner) throws Exception {
        MvcResult rolesResult = mockMvc.perform(get("/api/guilds/{guildId}/roles", guildId))
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

    private record SocketProbe(
        WebSocketSession session,
        BlockingQueue<String> messages,
        BlockingQueue<String> closeStatuses
    ) {
        String nextMessage() throws InterruptedException {
            String message = messages.poll(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS);
            assertThat(message).isNotNull();
            return message;
        }

        boolean awaitClose() throws InterruptedException {
            return closeStatuses.poll(Duration.ofSeconds(5).toMillis(), TimeUnit.MILLISECONDS) != null;
        }
    }
}
