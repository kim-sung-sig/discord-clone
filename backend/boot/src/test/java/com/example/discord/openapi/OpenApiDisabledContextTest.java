package com.example.discord.openapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.swagger.v3.core.jackson.ModelResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Deployed profiles run with springdoc.api-docs.enabled=false (main application.yml). The test resources'
 * application.yml shadows that file, so pin the deployed value here: the application must start and not serve docs.
 */
@SpringBootTest(properties = "springdoc.api-docs.enabled=false")
@AutoConfigureMockMvc
class OpenApiDisabledContextTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext context;

    @Test
    void applicationStartsWithoutApiDocs() throws Exception {
        assertThat(context.getBeansOfType(ModelResolver.class)).isEmpty();
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
