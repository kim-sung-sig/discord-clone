package com.example.discord.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.discord.DiscordApplication;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.MDC;

@SpringBootTest(classes = {DiscordApplication.class, OperationalHardeningFilterTest.ObservabilityProbeController.class})
@AutoConfigureMockMvc
class OperationalHardeningFilterTest {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String SAFE_REQUEST_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void apiCatalogGeneratesRequestIdAndSetsSecurityHeadersWhenMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/premium/catalog"))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("Referrer-Policy", "no-referrer"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
            .andExpect(header().string("Permissions-Policy", "camera=(), microphone=(), geolocation=()"))
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn();

        assertThat(result.getResponse().getHeader(REQUEST_ID_HEADER))
            .matches(SAFE_REQUEST_ID_PATTERN);
    }

    @Test
    void apiCatalogEchoesSafeIncomingRequestId() throws Exception {
        mockMvc.perform(get("/api/premium/catalog")
                .header(REQUEST_ID_HEADER, "qa-request_123.trace"))
            .andExpect(status().isOk())
            .andExpect(header().string(REQUEST_ID_HEADER, "qa-request_123.trace"));
    }

    @Test
    void apiCatalogDoesNotReflectUnsafeIncomingRequestId() throws Exception {
        String unsafeRequestId = "bad header<script>";

        MvcResult result = mockMvc.perform(get("/api/premium/catalog")
                .header(REQUEST_ID_HEADER, unsafeRequestId))
            .andExpect(status().isOk())
            .andReturn();

        assertThat(result.getResponse().getHeader(REQUEST_ID_HEADER))
            .isNotEqualTo(unsafeRequestId)
            .matches(SAFE_REQUEST_ID_PATTERN);
    }

    @Test
    void apiPreflightAllowsLocalNuxtDevelopmentOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                .header("Origin", "http://127.0.0.1:3000")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type, x-request-id"))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:3000"))
            .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"))
            .andExpect(header().string("Access-Control-Allow-Headers", "Authorization,Content-Type,X-Request-Id"))
            .andExpect(header().string("Vary", "Origin"));
    }

    @Test
    void apiRequestIdIsAvailableInMdcAndClearedAfterRequest() throws Exception {
        mockMvc.perform(get("/api/observability/mdc")
                .header(REQUEST_ID_HEADER, "t17-request-123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requestId").value("t17-request-123"))
            .andExpect(jsonPath("$.method").value("GET"))
            .andExpect(jsonPath("$.path").value("/api/observability/mdc"));

        assertThat(MDC.get("request_id")).isNull();
        assertThat(MDC.get("http_method")).isNull();
        assertThat(MDC.get("http_path")).isNull();
        assertThat(MDC.get("http_status")).isNull();
    }

    @Test
    void apiRequestMetricsAndAuthFailureCounterAreRecorded() throws Exception {
        double requestCountBefore = timerCount("discord.api.requests", "GET", "/api/observability/mdc", "200");
        mockMvc.perform(get("/api/observability/mdc")
                .header(REQUEST_ID_HEADER, "t17-metrics-123"))
            .andExpect(status().isOk());

        assertThat(timerCount("discord.api.requests", "GET", "/api/observability/mdc", "200"))
            .isGreaterThan(requestCountBefore);

        double authFailuresBefore = authFailureCount("invalid_credentials");
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "t17-missing@example.com",
                      "password": "wrong password"
                    }
                    """))
            .andExpect(status().isUnauthorized());

        assertThat(authFailureCount("invalid_credentials")).isGreaterThan(authFailuresBefore);
    }

    @Test
    void apiMetricsNormalizeUuidPathSegments() throws Exception {
        String probeId = "28b67f6d-b715-4e36-9259-b8c61237f8af";

        mockMvc.perform(get("/api/observability/mdc/{id}", probeId)
                .header(REQUEST_ID_HEADER, "t17-normalized-path"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.path").value("/api/observability/mdc/{uuid}"));

        assertThat(timerCount("discord.api.requests", "GET", "/api/observability/mdc/{uuid}", "200"))
            .isGreaterThan(0);
    }

    private double timerCount(String name, String method, String path, String status) {
        var timer = meterRegistry.find(name)
            .tag("method", method)
            .tag("path", path)
            .tag("status", status)
            .timer();
        return timer == null ? 0.0 : timer.count();
    }

    private double authFailureCount(String outcome) {
        var counter = meterRegistry.find("discord.auth.failures")
            .tag("outcome", outcome)
            .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @RestController
    static class ObservabilityProbeController {
        @GetMapping("/api/observability/mdc")
        Map<String, String> mdc() {
            return Map.of(
                "requestId", valueOrEmpty(MDC.get("request_id")),
                "method", valueOrEmpty(MDC.get("http_method")),
                "path", valueOrEmpty(MDC.get("http_path"))
            );
        }

        @GetMapping("/api/observability/mdc/{id}")
        Map<String, String> mdcWithId(@PathVariable String id) {
            return Map.of(
                "id", id,
                "path", valueOrEmpty(MDC.get("http_path"))
            );
        }

        private static String valueOrEmpty(String value) {
            return value == null ? "" : value;
        }
    }
}
