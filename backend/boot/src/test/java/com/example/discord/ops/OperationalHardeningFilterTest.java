package com.example.discord.ops;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class OperationalHardeningFilterTest {
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String SAFE_REQUEST_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";

    @Autowired
    private MockMvc mockMvc;

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
}
