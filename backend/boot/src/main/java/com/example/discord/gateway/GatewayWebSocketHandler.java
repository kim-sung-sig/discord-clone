package com.example.discord.gateway;

import com.example.discord.auth.AuthenticatedUserResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
final class GatewayWebSocketHandler extends TextWebSocketHandler {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final InMemoryGatewayService gatewayService;
    private final AuthenticatedUserResolver authenticatedUserResolver;
    private final ObjectMapper objectMapper;
    private final Map<String, ClientSocket> clients = new ConcurrentHashMap<>();

    GatewayWebSocketHandler(
        InMemoryGatewayService gatewayService,
        AuthenticatedUserResolver authenticatedUserResolver,
        ObjectMapper objectMapper
    ) {
        this.gatewayService = gatewayService;
        this.authenticatedUserResolver = authenticatedUserResolver;
        this.objectMapper = objectMapper;
        this.gatewayService.addEventListener(ignored -> flushEventsToClients());
    }

    @Override
    protected void handleTextMessage(WebSocketSession socket, TextMessage message) {
        try {
            Map<String, Object> body = objectMapper.readValue(message.getPayload(), MAP_TYPE);
            String type = stringValue(body.get("type"));
            switch (type) {
                case "IDENTIFY" -> identify(socket, body);
                case "HEARTBEAT" -> heartbeat(socket, body);
                case "RESUME" -> resume(socket, body);
                default -> send(socket, Map.of("type", "ERROR", "code", "UNKNOWN_OPCODE", "message", "unknown gateway message type"));
            }
        } catch (ResponseStatusException exception) {
            send(socket, Map.of("type", "ERROR", "code", "UNAUTHORIZED", "message", "authentication failed"));
            close(socket, CloseStatus.NOT_ACCEPTABLE.withReason("authentication failed"));
        } catch (RuntimeException | IOException exception) {
            send(socket, Map.of("type", "ERROR", "code", "INVALID_PAYLOAD", "message", "invalid gateway message"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        clients.remove(session.getId());
    }

    void closeClosedGatewaySessions() {
        for (String socketId : List.copyOf(clients.keySet())) {
            ClientSocket client = clients.get(socketId);
            if (client == null) {
                continue;
            }
            try {
                if (gatewayService.session(client.gatewaySessionId()).closed()) {
                    close(client.socket(), CloseStatus.SESSION_NOT_RELIABLE.withReason("heartbeat timeout"));
                    clients.remove(socketId);
                }
            } catch (GatewaySessionNotFoundException exception) {
                close(client.socket(), CloseStatus.SESSION_NOT_RELIABLE.withReason("gateway session missing"));
                clients.remove(socketId);
            }
        }
    }

    private void identify(WebSocketSession socket, Map<String, Object> body) {
        UUID userId = authenticatedUserResolver.requireUserId(bearerHeader(stringValue(body.get("accessToken"))));
        GatewayIdentifyResult result = gatewayService.identify(userId);
        clients.put(socket.getId(), new ClientSocket(socket, result.session().id(), userId, result.session().lastDeliveredSequence()));
        send(socket, Map.of(
            "type", "READY",
            "sessionId", result.session().id().toString(),
            "userId", userId.toString(),
            "sequence", result.ready().sequence()
        ));
    }

    private void heartbeat(WebSocketSession socket, Map<String, Object> body) {
        ClientSocket client = requireClient(socket);
        long lastSequence = longValue(body.get("lastSequence"), client.lastSequence());
        client = client.withLastSequence(lastSequence);
        clients.put(socket.getId(), client);
        GatewayHeartbeatResult result = gatewayService.heartbeat(client.gatewaySessionId(), client.userId());
        send(socket, Map.of(
            "type", "HEARTBEAT_ACK",
            "sequence", result.ack().sequence(),
            "serverTime", Instant.now().toString()
        ));
    }

    private void resume(WebSocketSession socket, Map<String, Object> body) {
        UUID userId = authenticatedUserResolver.requireUserId(bearerHeader(stringValue(body.get("accessToken"))));
        UUID gatewaySessionId = UUID.fromString(stringValue(body.get("sessionId")));
        long lastSequence = longValue(body.get("lastSequence"), 0L);
        GatewayResumeResult result = gatewayService.resume(gatewaySessionId, userId, lastSequence);
        clients.put(socket.getId(), new ClientSocket(socket, gatewaySessionId, userId, lastSequence));
        send(socket, Map.of(
            "type", "RESUMED",
            "sessionId", gatewaySessionId.toString(),
            "replayedCount", result.events().size(),
            "sequence", result.resumed().sequence()
        ));
        deliver(socket.getId(), result.events());
    }

    private void flushEventsToClients() {
        for (String socketId : List.copyOf(clients.keySet())) {
            ClientSocket client = clients.get(socketId);
            if (client == null || !client.socket().isOpen()) {
                clients.remove(socketId);
                continue;
            }
            List<GatewayEvent> events = gatewayService.poll(client.gatewaySessionId(), client.userId(), client.lastSequence());
            deliver(socketId, events);
        }
    }

    private void deliver(String socketId, List<GatewayEvent> events) {
        ClientSocket client = clients.get(socketId);
        if (client == null || events.isEmpty()) {
            return;
        }
        long lastSequence = client.lastSequence();
        for (GatewayEvent event : events) {
            send(client.socket(), Map.of(
                "type", "EVENT",
                "sequence", event.sequence(),
                "eventType", event.type(),
                "guildId", event.guildId() == null ? "" : event.guildId().toString(),
                "channelId", event.channelId() == null ? "" : event.channelId().toString(),
                "payload", event.payload(),
                "createdAt", event.createdAt().toString()
            ));
            lastSequence = Math.max(lastSequence, event.sequence());
        }
        clients.put(socketId, client.withLastSequence(lastSequence));
    }

    private ClientSocket requireClient(WebSocketSession socket) {
        ClientSocket client = clients.get(socket.getId());
        if (client == null) {
            throw new IllegalArgumentException("identify required");
        }
        return client;
    }

    private void send(WebSocketSession socket, Map<String, Object> payload) {
        if (!socket.isOpen()) {
            return;
        }
        synchronized (socket) {
            try {
                socket.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (IOException exception) {
                close(socket, CloseStatus.SERVER_ERROR);
            }
        }
    }

    private static void close(WebSocketSession socket, CloseStatus status) {
        try {
            socket.close(status);
        } catch (IOException ignored) {
            // Session is already unusable.
        }
    }

    private static String bearerHeader(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return null;
        }
        return accessToken.startsWith("Bearer ") ? accessToken : "Bearer " + accessToken;
    }

    private static String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private static long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null || value.toString().isBlank()) {
            return fallback;
        }
        return Long.parseLong(value.toString());
    }

    private record ClientSocket(WebSocketSession socket, UUID gatewaySessionId, UUID userId, long lastSequence) {
        ClientSocket withLastSequence(long nextLastSequence) {
            return new ClientSocket(socket, gatewaySessionId, userId, nextLastSequence);
        }
    }
}
