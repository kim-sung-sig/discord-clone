package com.example.discord.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.discord.gateway.GatewayResyncRequiredException;
import jakarta.servlet.RequestDispatcher;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Import(ApiExceptionHandlerTest.FailureController.class)
@ExtendWith(OutputCaptureExtension.class)
class ApiExceptionHandlerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void unauthorizedUsesProblemDetailWithSameRequestIdAsHeader() throws Exception {
        mvc.perform(get("/api/users/@me/guilds").header("X-Request-Id", "t214-request"))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Unauthorized"))
            .andExpect(jsonPath("$.status").value(401))
            .andExpect(jsonPath("$.detail").isString())
            .andExpect(jsonPath("$.instance").value("/api/users/@me/guilds"))
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.requestId").value("t214-request"))
            .andExpect(header().string("X-Request-Id", "t214-request"))
            .andExpect(jsonPath("$.message").doesNotExist());
    }

    @Test
    void loginLockHasDifferentCodeFromForbidden() throws Exception {
        mvc.perform(get("/api/problem-test/locked"))
            .andExpect(status().isLocked())
            .andExpect(jsonPath("$.code").value("LOGIN_LOCKED"));
        mvc.perform(get("/api/problem-test/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void gatewayResyncKeepsRecoveryCode() throws Exception {
        mvc.perform(get("/api/problem-test/resync"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("RESYNC_REQUIRED"));
    }

    @Test
    void malformedJsonDoesNotExposeInput() throws Exception {
        mvc.perform(post("/api/problem-test/body").contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"secret-token"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-token"));
    }

    @Test
    void unexpectedExceptionDoesNotExposeInternalDetails(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/problem-test/internal"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                .doesNotContain("database-password", "IllegalStateException"));
        assertThat(output.getAll()).contains("exception_type=java.lang.IllegalStateException",
            "ApiExceptionHandlerTest$FailureController.fail", "request_id=").doesNotContain("database-password");
    }

    @Test
    void frameworkMethodAndPathErrorsUseProblemDetails() throws Exception {
        mvc.perform(post("/api/problem-test/forbidden"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
            .andExpect(header().exists("Allow"));
        mvc.perform(get("/api/no-such-route-t214"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void invalidRequestIdIsReplacedAndQueryIsNotEchoed() throws Exception {
        var result = mvc.perform(get("/api/problem-test/forbidden?token=private-query")
                .header("X-Request-Id", "unsafe id"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.instance").value("/api/problem-test/forbidden"))
            .andReturn();
        String id = result.getResponse().getHeader("X-Request-Id");
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(result.getResponse().getContentAsString()).contains(id).doesNotContain("private-query");
    }

    @Test
    void servletErrorDispatchUsesSameSafeProblemContract() throws Exception {
        mvc.perform(get("/error")
                .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/api/failed-request")
                .requestAttr(RequestDispatcher.ERROR_MESSAGE, "database-password"))
            .andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.instance").value("/api/failed-request"))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.requestId").isString())
            .andExpect(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain("database-password"));
    }

    @RestController
    static class FailureController {
        @GetMapping("/api/problem-test/{kind}")
        void fail(@PathVariable String kind) {
            switch (kind) {
                case "locked" -> throw new LoginLockedException();
                case "forbidden" -> throw new ResponseStatusException(HttpStatus.FORBIDDEN, "forbidden");
                case "resync" -> throw new GatewayResyncRequiredException("retention expired");
                default -> throw new IllegalStateException("database-password");
            }
        }

        @PostMapping("/api/problem-test/body")
        void body(@RequestBody AuthController.LoginRequest body) {
        }
    }
}
