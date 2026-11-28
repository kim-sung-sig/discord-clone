package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

abstract class GatewayWebSocketTestSupport {
    @LocalServerPort
    private int port;

    @Autowired
    MockMvc mockMvc;

    SocketProbe connect() throws Exception {
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

    String createGuild(AuthSession owner) throws Exception {
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

    AuthSession signup(String username) throws Exception {
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

    record AuthSession(String accessToken, UUID userId) {
        String bearer() {
            return "Bearer " + accessToken;
        }
    }

    record SocketProbe(
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
