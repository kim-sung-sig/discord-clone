package com.example.discord.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.socket.TextMessage;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "discord.gateway.heartbeat-timeout-ms=100"
)
@AutoConfigureMockMvc
class GatewayWebSocketHeartbeatTimeoutTest extends GatewayWebSocketTestSupport {
    @Autowired
    private GatewaySessionMaintenance sessionMaintenance;

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
}
